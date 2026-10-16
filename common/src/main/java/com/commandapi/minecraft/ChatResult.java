package com.commandapi.minecraft;

/** Immutable submission/execution outcome. Legacy success describes acceptance, not world effects. */
public final class ChatResult {
    private final boolean success;
    private final String message, stage, code;
    private final Integer returnValue;
    private final int callbacks;
    private ChatResult(boolean success, String message, String stage, String code, Integer value, int callbacks) {
        this.success = success; this.message = message; this.stage = stage; this.code = code;
        this.returnValue = value; this.callbacks = callbacks;
    }
    public static ChatResult ok(String message) { return new ChatResult(true, message, "dispatched", "DISPATCHED", null, 0); }
    public static ChatResult accepted(String message) { return new ChatResult(true, message, "accepted", "ACCEPTED", null, 0); }
    public static ChatResult failure(String message) { return failure(message, "EXECUTION_FAILED"); }
    public static ChatResult failure(String message, String code) { return new ChatResult(false, message, "failed", code, null, 0); }
    public static ChatResult unknown(String message, String code) { return new ChatResult(false, message, "unknown", code, null, 0); }
    public static ChatResult execution(boolean success, int value, int callbacks) {
        return new ChatResult(success, success ? "Command execution confirmed" : "Command execution failed",
                success ? "executed" : "failed", success ? "EXECUTED" : "EXECUTION_FAILED", value, callbacks);
    }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getStage() { return stage; }
    public String getCode() { return code; }
    public Integer getReturnValue() { return returnValue; }
    public int getCallbacks() { return callbacks; }
}
