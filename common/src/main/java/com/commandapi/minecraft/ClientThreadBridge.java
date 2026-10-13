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
    @Override public void configure(com.commandapi.config.ApiConfig config) { configuration = config; configureNative(config); }
    protected void configureNative(com.commandapi.config.ApiConfig config) { }
    protected final boolean elevatedSingleplayer() { return configuration.isElevatedSingleplayer(); }

    /** How long an HTTP request waits for the client thread to run the send. */
    private static final long TIMEOUT_SECONDS = 5;

    /** Minecraft's client-thread executor. */
    protected abstract Executor clientExecutor();

    /** True when already on Minecraft's client thread. */
    protected abstract boolean isClientThread();

    /** Performs the send; always invoked on the client thread. */
    protected abstract ChatResult sendChatOnClientThread(String text);

    /** Optional native transport, invoked on the client thread. */
    protected CompletableFuture<ChatResult> nativeCommand(String text, CancellationToken cancellation) { return null; }

    @Override public final CompletableFuture<ChatResult> submitChat(String text, Object session, CancellationToken cancellation) {
        CompletableFuture<ChatResult> future = new CompletableFuture<>();
        Runnable send = () -> {
            if (future.isDone() || cancellation.isCancelled()) {
                future.complete(ChatResult.failure("Cancelled before dispatch", "CANCELLED")); return;
            }
            if (session == null || !java.util.Objects.equals(session, sessionIdentity())) {
                future.complete(ChatResult.failure("Session changed", "SESSION_CHANGED")); return;
            }
            try {
                CompletableFuture<ChatResult> nativeResult = text.startsWith("/") ? nativeCommand(text, cancellation) : null;
                if (nativeResult == null) future.complete(sendChatOnClientThread(text));
                else nativeResult.whenComplete((result, error) -> {
                    if (error != null) future.complete(ChatResult.failure("Native transport failed", "TRANSPORT_FAILED"));
                    else future.complete(result);
                });
            } catch (Throwable error) { future.complete(ChatResult.failure("Dispatch failed", "DISPATCH_EXCEPTION")); }
        };
        try { if (isClientThread()) send.run(); else clientExecutor().execute(send); }
        catch (RuntimeException error) { future.complete(ChatResult.failure("Could not schedule on client thread", "SCHEDULER_UNAVAILABLE")); }
        return future;
    }

    @Override public final ChatResult sendChat(String text) { return sendChat(text, sessionIdentity()); }
    @Override public final ChatResult sendChat(String text, Object session) {
        CancellationToken cancellation = new CancellationToken();
        CompletableFuture<ChatResult> future = submitChat(text, session, cancellation);
        if (isClientThread() && !future.isDone()) return ChatResult.accepted("Command queued without blocking the Minecraft thread");
        try { return future.get(configuration.getExecutionTimeoutSeconds(), TimeUnit.SECONDS); }
        catch (TimeoutException error) { cancellation.cancel(); return ChatResult.unknown("Execution timeout; do not retry automatically", "EXECUTION_TIMEOUT"); }
        catch (InterruptedException error) { cancellation.cancel(); Thread.currentThread().interrupt(); return ChatResult.unknown("Wait cancelled", "WAIT_CANCELLED"); }
        catch (ExecutionException error) { return ChatResult.failure("Transport failed", "TRANSPORT_FAILED"); }
    }

    protected final <T> T onClientThread(Callable<T> action, T timeoutValue,
                                         java.util.function.Function<String, T> failure) {
        if (isClientThread()) {
            try {
                return action.call();
            } catch (Exception e) {
                return failure.apply("Execution failed");
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
                    future.complete(failure.apply("Execution failed"));
                }
            });
        } catch (RuntimeException e) {
            return failure.apply("Could not schedule on client thread");
        }

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(false);
            return timeoutValue;
        } catch (ExecutionException e) {
            return failure.apply("Execution failed");
        } catch (InterruptedException e) {
            future.cancel(false);
            Thread.currentThread().interrupt();
            return failure.apply("Interrupted while waiting for the Minecraft client thread");
        }
    }
}
