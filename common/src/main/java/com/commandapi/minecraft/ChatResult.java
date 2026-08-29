package com.commandapi.minecraft;

/**
 * Outcome of a chat/command send attempt.
 *
 * <p>Produced by a {@link MinecraftBridge} implementation and consumed by the
 * shared HTTP layer, which never touches Minecraft classes itself.</p>
 */
public final class ChatResult {
    private final boolean success;
    private final String message;

    private ChatResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ChatResult ok(String message) {
        return new ChatResult(true, message);
    }

    public static ChatResult failure(String message) {
        return new ChatResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
