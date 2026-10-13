package com.commandapi.minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
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

    private volatile com.commandapi.config.ApiConfig configuration = com.commandapi.config.ApiConfig.defaults();
    @Override public void configure(com.commandapi.config.ApiConfig config) { configuration = config; }
    protected final boolean elevatedSingleplayer() { return configuration.isElevatedSingleplayer(); }

    /** How long an HTTP request waits for the client thread to run the send. */
    private static final long TIMEOUT_SECONDS = 5;

    /** Minecraft's client-thread executor. */
    protected abstract Executor clientExecutor();

    /** True when already on Minecraft's client thread. */
    protected abstract boolean isClientThread();

    /** Performs the send; always invoked on the client thread. */
    protected abstract ChatResult sendChatOnClientThread(String text);

    /** Optional native command execution, captured on the client thread. */
    protected CompletableFuture<ChatResult> nativeCommand(String text) { return null; }

    @Override
    public final ChatResult sendChat(String text) { return sendChat(text, null); }

    @Override
    public final ChatResult sendChat(String text, Object session) {
        if (text.startsWith("/")) {
            CompletableFuture<ChatResult> nativeResult = onClientThread(() -> session != null && !java.util.Objects.equals(session, sessionIdentity())
                    ? CompletableFuture.completedFuture(ChatResult.failure("Session changed")) : nativeCommand(text),
                    CompletableFuture.completedFuture(ChatResult.failure("Client thread unavailable")),
                    error -> CompletableFuture.completedFuture(ChatResult.failure(error)));
            if (nativeResult != null) {
                if (isClientThread() && !nativeResult.isDone())
                    return ChatResult.accepted("Native command queued; server thread will process it");
                try { return nativeResult.get(configuration.getExecutionTimeoutSeconds(), TimeUnit.SECONDS); }
                catch (TimeoutException e) {
                    nativeResult.cancel(false);
                    return ChatResult.unknown("Native command timed out; do not retry automatically", "EXECUTION_TIMEOUT");
                }
                catch (InterruptedException e) {
                    nativeResult.cancel(false);
                    Thread.currentThread().interrupt();
                    return ChatResult.unknown("Native wait cancelled; an already running command may finish", "WAIT_CANCELLED");
                } catch (ExecutionException e) { return ChatResult.failure("Native execution failed", "DISPATCH_EXCEPTION"); }
            }
        }
        return onClientThread(() -> session != null && !java.util.Objects.equals(session, sessionIdentity())
                        ? ChatResult.failure("Session changed") : sendChatOnClientThread(text),
                ChatResult.failure("Timed out waiting for the Minecraft client thread"),
                error -> ChatResult.failure(error));
    }

    protected final <T> T onClientThread(Callable<T> action, T timeoutValue,
                                         java.util.function.Function<String, T> failure) {
        if (isClientThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                return failure.apply("Error: " + e);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            clientExecutor().execute(() -> {
                if (future.isDone()) {
                    return;
                }
                try {
                    future.complete(action.call());
                } catch (Throwable t) {
                    future.complete(failure.apply("Error: " + t));
                }
            });
        } catch (RuntimeException e) {
            return failure.apply("Could not schedule on client thread: " + e);
        }

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            return timeoutValue;
        } catch (ExecutionException e) {
            return failure.apply("Error: " + e.getCause());
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            return failure.apply("Interrupted while waiting for the Minecraft client thread");
        }
    }
}
