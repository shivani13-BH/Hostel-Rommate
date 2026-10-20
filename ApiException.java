package com.hostelmate.api;

/** Thrown by route handlers to send a clean error response to the browser. */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
