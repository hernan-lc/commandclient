package com.commandapi.api;

/**
 * Request limits. The API can send chat as the player, so a request must never
 * be able to consume unbounded memory or flood the server with messages.
 *
 * <p>The message length mirrors Minecraft's own chat box limit; anything longer
 * would be rejected or truncated by the game anyway.</p>
 */
public final class ApiLimits {

    /** Maximum accepted request body, in bytes. */
    public static final int MAX_BODY_BYTES = 64 * 1024;

    /** Maximum messages in one {@code messages} array. */
    public static final int MAX_BATCH_SIZE = 32;

    /** Maximum characters per message, matching Minecraft's chat limit. */
    public static final int MAX_MESSAGE_LENGTH = 256;

    private ApiLimits() {
    }
}
