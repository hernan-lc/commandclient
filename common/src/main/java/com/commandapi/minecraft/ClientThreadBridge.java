package com.commandapi.minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Base for bridges whose Minecraft calls must run on the client thread.
 *
 * <p>HTTP handlers run on arbitrary worker threads, so a version adapter must
 * not touch client state directly. This class schedules the work onto the
 * client executor and lets the caller wait for the outcome with a timeout,
 * which keeps that plumbing out of every version module.</p>
 *
 * <p>Only JDK types are used here, so this stays version independent; the
 * subclass supplies Minecraft's executor.</p>
 */
public abstract class ClientThreadBridge implements MinecraftBridge {

    /** How long an HTTP request waits for the client thread to run the send. */
    private static final long TIMEOUT_SECONDS = 5;

    /** Minecraft's client-thread executor. */
    protected abstract Executor clientExecutor();

    /** Performs the send; always invoked on the client thread. */
    protected abstract ChatResult sendChatOnClientThread(String text);

    @Override
    public final ChatResult sendChat(String text) {
        CompletableFuture<ChatResult> future = new CompletableFuture<>();
        try {
            clientExecutor().execute(() -> {
                try {
                    future.complete(sendChatOnClientThread(text));
                } catch (Throwable t) {
                    future.complete(ChatResult.failure("Error: " + t));
                }
            });
        } catch (RuntimeException e) {
            return ChatResult.failure("Could not schedule on client thread: " + e);
        }

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return ChatResult.failure("Timed out waiting for the Minecraft client thread");
        } catch (ExecutionException e) {
            return ChatResult.failure("Error: " + e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChatResult.failure("Interrupted while waiting for the Minecraft client thread");
        }
    }
}
