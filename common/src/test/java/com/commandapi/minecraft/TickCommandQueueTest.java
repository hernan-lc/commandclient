package com.commandapi.minecraft;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class TickCommandQueueTest {
    @Test void budgetBoundsCommandsAndCancellationSkipsDispatch() {
        TickCommandQueue queue = new TickCommandQueue(); AtomicInteger count = new AtomicInteger(); Object session = new Object();
        CancellationToken cancelled = new CancellationToken(); cancelled.cancel();
        queue.submit(session, cancelled, () -> { count.incrementAndGet(); return ChatResult.ok("sent"); }, 3);
        queue.submit(session, new CancellationToken(), () -> { count.incrementAndGet(); return ChatResult.ok("sent"); }, 3);
        queue.submit(session, new CancellationToken(), () -> { count.incrementAndGet(); return ChatResult.ok("sent"); }, 3);
        queue.drain(session, 2, Long.MAX_VALUE); assertEquals(1, count.get()); assertEquals(1, queue.size());
        queue.drain(session, 2, Long.MAX_VALUE); assertEquals(2, count.get());
    }
    @Test void oldSessionsAndShutdownNeverExecute() {
        TickCommandQueue queue = new TickCommandQueue(); AtomicInteger count = new AtomicInteger();
        java.util.concurrent.CompletableFuture<ChatResult> stale = queue.submit(new Object(), new CancellationToken(), () -> { count.incrementAndGet(); return ChatResult.ok("sent"); }, 1);
        queue.drain(new Object(), 1, Long.MAX_VALUE);
        assertEquals("SESSION_CHANGED", stale.join().getCode()); assertEquals(0, count.get());
        queue.close(); assertEquals("QUEUE_FULL", queue.submit(new Object(), new CancellationToken(), () -> ChatResult.ok("sent"), 1).join().getCode());
    }
    @Test void capacityAndResetResolvePendingFutures() {
        TickCommandQueue queue = new TickCommandQueue(); Object session = new Object();
        java.util.concurrent.CompletableFuture<ChatResult> pending = queue.submit(session, new CancellationToken(), () -> ChatResult.ok("sent"), 1);
        assertEquals("QUEUE_FULL", queue.submit(session, new CancellationToken(), () -> ChatResult.ok("sent"), 1).join().getCode());
        queue.reset(); assertEquals("SESSION_CHANGED", pending.join().getCode());
    }
}
