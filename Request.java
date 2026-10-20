package com.hostelmate.api;

import com.hostelmate.database.User;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Wraps an HttpExchange: parses query string and url-encoded form body. */
public class Request {
    public final HttpExchange ex;
    public final String method;
    public final String path;
    public final Map<String, String> params = new HashMap<>();
    public User user; // set by ApiServer when a valid token is present

    private static final int MAX_BODY = 64 * 1024;

    public Request(HttpExchange ex) throws IOException {
        this.ex = ex;
        this.method = ex.getRequestMethod().toUpperCase();
        this.path = ex.getRequestURI().getPath();
        parse(ex.getRequestURI().getRawQuery());
        if (method.equals("POST")) {
            byte[] body = ex.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) throw new ApiException(413, "Request is too large.");
            parse(new String(body, StandardCharsets.UTF_8));
        }
    }

    private void parse(String raw) {
        if (raw == null || raw.isBlank()) return;
        for (String pair : raw.split("&")) {
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            params.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
    }

    public String header(String name) {
        return ex.getRequestHeaders().getFirst(name);
    }

    public String str(String key) {
        String v = params.get(key);
        return v == null ? "" : v.trim();
    }

    public String required(String key, String label, int maxLen) {
        String v = str(key);
        if (v.isEmpty()) throw new ApiException(400, label + " is required.");
        if (v.length() > maxLen) throw new ApiException(400, label + " must be at most " + maxLen + " characters.");
        return v;
    }

    public int integer(String key, String label) {
        try {
            return Integer.parseInt(str(key));
        } catch (NumberFormatException e) {
            throw new ApiException(400, label + " must be a whole number.");
        }
    }

    public int integer(String key, String label, int min, int max) {
        int v = integer(key, label);
        if (v < min || v > max) throw new ApiException(400, label + " must be between " + min + " and " + max + ".");
        return v;
    }
}
