package br.com.devlopes.smartbudget.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SessionContextFilter extends OncePerRequestFilter {

    public static final String SESSION_HEADER = "X-Session-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String headerValue = request.getHeader(SESSION_HEADER);
            SessionContext.setCurrentSessionId(headerValue);
            response.setHeader(SESSION_HEADER, SessionContext.getCurrentSessionId());
            filterChain.doFilter(request, response);
        } finally {
            SessionContext.clear();
        }
    }
}
