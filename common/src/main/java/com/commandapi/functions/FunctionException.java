package com.commandapi.functions;

/** Checked failure for function parsing, validation, resolution and compilation. */
public final class FunctionException extends Exception {
    private final String code;

    public FunctionException(String message) {
        this(message, "FUNCTION_ERROR");
    }

    public FunctionException(String message, String code) {
        super(message);
        this.code = code == null ? "FUNCTION_ERROR" : code;
    }

    public FunctionException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code == null ? "FUNCTION_ERROR" : code;
    }

    public String getCode() {
        return code;
    }
}
