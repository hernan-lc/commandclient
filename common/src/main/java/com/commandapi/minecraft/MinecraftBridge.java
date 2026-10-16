package com.commandapi.minecraft;

/**
 * The only seam between the shared application code and a specific Minecraft
 * version. Every Minecraft API call lives in a version module's implementation
 * of this interface; shared code must never import Minecraft classes.
 *
 * <p>Implementations are called from HTTP worker threads, so they are
 * responsible for hopping onto the Minecraft client thread when the underlying
 * API requires it.</p>
 */
public interface MinecraftBridge {

    /** @return true when a client player exists (i.e. the user is in a world). */
    boolean isInWorld();

    /** @return the local player's name, or null when not in a world. */
    String getPlayerName();

    /**
     * Sends {@code text} as the local player: a chat message, or a command when
     * it starts with {@code /}.
     */
    ChatResult sendChat(String text);

    /** Checks session affinity before sending queued work. */
    default ChatResult sendChat(String text, Object session) {
        return java.util.Objects.equals(session, sessionIdentity()) ? sendChat(text)
                : ChatResult.failure("Session changed");
    }

    default java.util.concurrent.CompletableFuture<ChatResult> submitChat(String text, Object session, CancellationToken cancellation) {
        if (cancellation.isCancelled()) return java.util.concurrent.CompletableFuture.completedFuture(ChatResult.failure("Cancelled", "CANCELLED"));
        return java.util.concurrent.CompletableFuture.completedFuture(sendChat(text, session));
    }

    default void configure(com.commandapi.config.ApiConfig config) { }

    default com.google.gson.JsonObject capabilities() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("async_jobs", true); return json;
    }

    default String executionMode() { return "client-chat"; }

    /** Stable identity for the current connection; jobs cannot cross sessions. */
    default Object sessionIdentity() { return getPlayerName(); }

}
