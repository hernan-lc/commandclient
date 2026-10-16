package com.commandapi.minecraft;
import java.util.*;
import java.util.concurrent.*;
/** Bounded cooperative dispatcher. Drain only on the thread owning the world. */
public final class TickCommandQueue {
    private final ArrayDeque<Entry> pending = new ArrayDeque<>();
    private boolean closed;
    public synchronized CompletableFuture<ChatResult> submit(Object session, CancellationToken token, Callable<ChatResult> action, int capacity) {
        CompletableFuture<ChatResult> future = new CompletableFuture<>();
        if (closed || pending.size() >= capacity) { future.complete(ChatResult.failure("Tick queue unavailable", "QUEUE_FULL")); return future; }
        pending.add(new Entry(session, token, action, future)); return future;
    }
    public void drain(Object currentSession, int commands, long budgetNanos) {
        long start = System.nanoTime();
        for (int count = 0; count < commands && (count == 0 || System.nanoTime() - start < budgetNanos); count++) {
            Entry entry;
            synchronized (this) { entry = pending.poll(); }
            if (entry == null) return;
            if (entry.token.isCancelled() || entry.future.isDone()) { entry.future.complete(ChatResult.failure("Cancelled", "CANCELLED")); continue; }
            if (!Objects.equals(entry.session, currentSession)) { entry.future.complete(ChatResult.failure("Session changed", "SESSION_CHANGED")); continue; }
            try { entry.future.complete(entry.action.call()); }
            catch (Throwable error) { entry.future.complete(ChatResult.failure("Dispatcher exception", "DISPATCH_EXCEPTION")); }
        }
    }
    public synchronized void reset() {
        for (Entry entry : pending) entry.future.complete(ChatResult.failure("Session ended", "SESSION_CHANGED"));
        pending.clear();
    }
    public synchronized void close() { closed = true; reset(); }
    public synchronized int size() { return pending.size(); }
    private static final class Entry {
        final Object session; final CancellationToken token; final Callable<ChatResult> action; final CompletableFuture<ChatResult> future;
        Entry(Object session, CancellationToken token, Callable<ChatResult> action, CompletableFuture<ChatResult> future) {
            this.session = session; this.token = token; this.action = action; this.future = future;
        }
    }
}
