package com.commandapi.api;
import com.commandapi.config.*;
import com.commandapi.minecraft.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class CommandSchedulerTest {
    static final class Bridge implements MinecraftBridge {
        final Object session = new Object(); final CompletableFuture<ChatResult> pending = new CompletableFuture<>();
        final CountDownLatch entered = new CountDownLatch(1); volatile int sends;
        public boolean isInWorld() { return true; } public String getPlayerName() { return "p"; }
        public Object sessionIdentity() { return session; }
        public ChatResult sendChat(String text) { throw new AssertionError("must use asynchronous bridge"); }
        public CompletableFuture<ChatResult> submitChat(String text, Object session, CancellationToken token) { sends++; entered.countDown(); return pending; }
    }
    ApiConfig limits(int jobs, int commands) {
        return new ApiConfig("127.0.0.1", 0, "secret", true, true, false, true, Arrays.asList("*"), jobs, commands, 1, 1, 1, 1, 1, 1);
    }
    @Test void boundedCapacityAndQueuedCancellation() throws Exception {
        Bridge bridge = new Bridge();
        try (CommandScheduler scheduler = new CommandScheduler(bridge, limits(2, 2))) {
            CommandScheduler.Work first = scheduler.submit(Arrays.asList("/one")); assertTrue(bridge.entered.await(2, TimeUnit.SECONDS));
            CommandScheduler.Work second = scheduler.submit(Arrays.asList("/two"));
            assertNull(scheduler.submit(Arrays.asList("/three")));
            second.cancel("test"); bridge.pending.complete(ChatResult.ok("sent")); first.await(2);
            second.await(2); assertEquals("cancelled", second.snapshot().get("state").getAsString());
            assertEquals(1, bridge.sends);
        }
    }
    @Test void cancellationAndCompletionCannotChangeTerminalState() throws Exception {
        Bridge bridge = new Bridge();
        try (CommandScheduler scheduler = new CommandScheduler(bridge, limits(3, 3))) {
            CommandScheduler.Work work = scheduler.submit(Arrays.asList("/one", "/two"));
            assertTrue(bridge.entered.await(2, TimeUnit.SECONDS));
            List<Thread> racers = new ArrayList<>();
            for (int i = 0; i < 8; i++) { Thread t = new Thread(() -> work.cancel("race")); racers.add(t); t.start(); }
            for (Thread t : racers) t.join();
            bridge.pending.complete(ChatResult.ok("sent")); work.await(2);
            assertEquals("cancelled", work.snapshot().get("state").getAsString()); assertEquals(1, bridge.sends);
        }
    }
    @Test void shutdownCancelsQueuedWorkWithoutInterruptingDispatch() throws Exception {
        Bridge bridge = new Bridge(); CommandScheduler scheduler = new CommandScheduler(bridge, limits(2, 2));
        CommandScheduler.Work first = scheduler.submit(Arrays.asList("/one")); assertTrue(bridge.entered.await(2, TimeUnit.SECONDS));
        CommandScheduler.Work second = scheduler.submit(Arrays.asList("/two")); scheduler.close();
        assertEquals("cancelled", first.snapshot().get("state").getAsString());
        assertEquals("cancelled", second.snapshot().get("state").getAsString());
        assertFalse(bridge.pending.isCancelled()); assertNull(scheduler.submit(Arrays.asList("/late")));
    }
    @Test void timeoutStopsRemainingCommandsAndReportsUnknown() throws Exception {
        Bridge bridge = new Bridge();
        try (CommandScheduler scheduler = new CommandScheduler(bridge, limits(2, 2))) {
            CommandScheduler.Work work = scheduler.submit(Arrays.asList("/one", "/two")); work.await(3);
            assertEquals("failed", work.snapshot().get("state").getAsString());
            assertEquals("unknown", work.results().get(0).get("execution_status").getAsString()); assertEquals(1, bridge.sends);
        }
    }
}
