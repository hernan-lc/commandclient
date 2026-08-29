package com.commandapi.api;

/** Signals a request body over {@link ApiLimits#MAX_BODY_BYTES} (HTTP 413). */
public class RequestTooLargeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RequestTooLargeException(String message) {
        super(message);
    }
}
