package br.com.devlopes.smartbudget.config;

public final class SessionContext {

    public static final String DEFAULT_SESSION_ID = "default-session";
    private static final int MAX_SESSION_ID_LENGTH = 64;

    private static final ThreadLocal<String> CURRENT_SESSION = new ThreadLocal<>();

    private SessionContext() {
    }

    public static void setCurrentSessionId(String rawSessionId) {
        CURRENT_SESSION.set(sanitize(rawSessionId));
    }

    public static String getCurrentSessionId() {
        String sessionId = CURRENT_SESSION.get();
        return (sessionId == null || sessionId.isBlank()) ? DEFAULT_SESSION_ID : sessionId;
    }

    public static void clear() {
        CURRENT_SESSION.remove();
    }

    public static String sanitize(String rawSessionId) {
        if (rawSessionId == null || rawSessionId.isBlank()) {
            return DEFAULT_SESSION_ID;
        }
        String cleaned = rawSessionId.trim().replaceAll("[^a-zA-Z0-9_-]", "");
        if (cleaned.isEmpty()) {
            return DEFAULT_SESSION_ID;
        }
        return cleaned.length() > MAX_SESSION_ID_LENGTH
                ? cleaned.substring(0, MAX_SESSION_ID_LENGTH)
                : cleaned;
    }
}
