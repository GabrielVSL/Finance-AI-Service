package br.com.devlopes.smartbudget.config;

import br.com.devlopes.smartbudget.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiSecurityFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED_KEYS = 5_000;

    private final ObjectMapper objectMapper;
    private final byte[] expectedApiKeyBytes;
    private final boolean apiKeyEnabled;
    private final boolean rateLimitEnabled;
    private final int aiRpm;
    private final int writeRpm;
    private final int readRpm;
    private final long maxJsonBodyBytes;
    private final boolean trustProxyHeaders;
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();

    public ApiSecurityFilter(
            ObjectMapper objectMapper,
            @Value("${app.security.internal-api-key:}") String internalApiKey,
            @Value("${app.security.rate-limit.enabled:true}") boolean rateLimitEnabled,
            @Value("${app.security.rate-limit.ai-requests-per-minute:15}") int aiRpm,
            @Value("${app.security.rate-limit.write-requests-per-minute:40}") int writeRpm,
            @Value("${app.security.rate-limit.read-requests-per-minute:120}") int readRpm,
            @Value("${app.security.max-json-body-bytes:65536}") long maxJsonBodyBytes,
            @Value("${app.security.trust-proxy-headers:false}") boolean trustProxyHeaders
    ) {
        this.objectMapper = objectMapper;
        String trimmedKey = internalApiKey != null ? internalApiKey.trim() : "";
        this.apiKeyEnabled = !trimmedKey.isEmpty();
        this.expectedApiKeyBytes = trimmedKey.getBytes(StandardCharsets.UTF_8);
        this.rateLimitEnabled = rateLimitEnabled;
        this.aiRpm = Math.max(1, aiRpm);
        this.writeRpm = Math.max(1, writeRpm);
        this.readRpm = Math.max(1, readRpm);
        this.maxJsonBodyBytes = Math.max(1024L, maxJsonBodyBytes);
        this.trustProxyHeaders = trustProxyHeaders;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        applySecurityHeaders(request, response);

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method) || "/health".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (apiKeyEnabled && isProtectedPath(path)) {
            String provided = request.getHeader(API_KEY_HEADER);
            if (!isValidApiKey(provided)) {
                writeError(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Não Autorizado",
                        "Credencial interna ausente ou inválida.",
                        path
                );
                return;
            }
        }

        if (isJsonBodyTooLarge(request, path)) {
            writeError(
                    response,
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Payload Muito Grande",
                    "O corpo da requisição excede o limite máximo permitido.",
                    path
            );
            return;
        }

        if (rateLimitEnabled && path.startsWith("/api/")) {
            RateCheckResult rateCheck = checkRateLimit(request, path, method);
            if (!rateCheck.allowed()) {
                response.setHeader("Retry-After", String.valueOf(rateCheck.retryAfterSeconds()));
                writeError(
                        response,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas Requisições",
                        "Limite temporário de requisições atingido. Tente novamente em instantes.",
                        path
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
        }
    }

    private boolean isProtectedPath(String path) {
        return path.startsWith("/api/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    private boolean isValidApiKey(String provided) {
        if (provided == null || provided.isBlank()) {
            return false;
        }
        byte[] providedBytes = provided.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedApiKeyBytes, providedBytes);
    }

    private boolean isJsonBodyTooLarge(HttpServletRequest request, String path) {
        if (!path.startsWith("/api/")) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")) {
            return false;
        }
        long declaredLength = request.getContentLengthLong();
        return declaredLength > maxJsonBodyBytes;
    }

    private RateCheckResult checkRateLimit(HttpServletRequest request, String path, String method) {
        long now = System.currentTimeMillis();
        cleanupExpiredBucketsIfNeeded(now);

        String bucketName;
        int limit;
        if (path.startsWith("/api/v1/assistant/")) {
            bucketName = "ai";
            limit = aiRpm;
        } else if ("POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)) {
            bucketName = "write";
            limit = writeRpm;
        } else {
            bucketName = "read";
            limit = readRpm;
        }

        String clientKey = bucketName + ":" + resolveClientKey(request);
        RateBucket bucket = buckets.compute(clientKey, (k, existing) -> {
            if (existing == null || existing.resetAt <= now) {
                return new RateBucket(now + WINDOW_MS);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        int count = bucket.count.get();
        if (count > limit) {
            long retryAfter = Math.max(1L, (long) Math.ceil((bucket.resetAt - now) / 1000.0));
            return new RateCheckResult(false, retryAfter);
        }
        return new RateCheckResult(true, 0L);
    }

    private String resolveClientKey(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first.length() > 64 ? first.substring(0, 64) : first;
                }
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr;
    }

    private void cleanupExpiredBucketsIfNeeded(long now) {
        if (buckets.size() < MAX_TRACKED_KEYS) {
            return;
        }
        buckets.entrySet().removeIf(entry -> entry.getValue().resetAt <= now);
    }

    private void writeError(
            HttpServletResponse response,
            HttpStatus status,
            String error,
            String message,
            String path
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        ApiErrorResponse body = new ApiErrorResponse(status.value(), error, message, path);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static final class RateBucket {
        final AtomicInteger count = new AtomicInteger(1);
        final long resetAt;

        RateBucket(long resetAt) {
            this.resetAt = resetAt;
        }
    }

    private record RateCheckResult(boolean allowed, long retryAfterSeconds) {
    }
}
