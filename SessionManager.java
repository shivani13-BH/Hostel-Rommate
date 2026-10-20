package com.hostelmate.api;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Token-based login sessions. Tokens expire after 8 hours. */
public class SessionManager {
    private static final long TTL_MS = 8L * 60 * 60 * 1000;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, long[]> sessions = new ConcurrentHashMap<>(); // token -> {userId, expiresAt}

    public String create(int userId) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new long[]{userId, System.currentTimeMillis() + TTL_MS});
        return token;
    }

    /** @return user id, or -1 if the token is unknown or expired. */
    public int resolve(String token) {
        if (token == null) return -1;
        long[] s = sessions.get(token);
        if (s == null) return -1;
        if (s[1] < System.currentTimeMillis()) {
            sessions.remove(token);
            return -1;
        }
        return (int) s[0];
    }

    public void destroy(String token) {
        if (token != null) sessions.remove(token);
    }
}
