package com.commandapi.api;

/** Signals a client error (HTTP 400) while parsing a request body. */
public class ApiRequestException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ApiRequestException(String message) {
        super(message);
    }
}
