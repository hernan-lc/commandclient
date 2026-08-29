package com.commandapi.minecraft;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client-thread hand-off is version independent, so it is tested here. */
class ClientThreadBridgeTest {

    /** Runs queued work on one dedicated thread, like Minecraft's client loop. */
    private static final class FakeClientThread implements Executor {
        private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(16);
        private final Thread thread = new Thread(() -> {
            try {
                while (true) {
                    queue.take().run();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "fake-client-thread");

        FakeClientThread() {
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void execute(Runnable command) {
            queue.add(command);
        }
    }

    private static final class TestBridge extends ClientThreadBridge {
        private final Executor executor;
        final AtomicReference<Thread> ranOn = new AtomicReference<>();

        TestBridge(Executor executor) {
            this.executor = executor;
        }

        @Override
        public boolean isInWorld() {
            return true;
        }

        @Override
        public String getPlayerName() {
            return "Steve";
        }

        @Override
        protected Executor clientExecutor() {
            return executor;
        }

        @Override
        protected ChatResult sendChatOnClientThread(String text) {
            ranOn.set(Thread.currentThread());
            if ("boom".equals(text)) {
                throw new IllegalStateException("failed");
            }
            return ChatResult.ok("sent " + text);
        }
    }

    @Test
    void runsTheSendOnTheClientThreadAndReturnsItsResult() {
        TestBridge bridge = new TestBridge(new FakeClientThread());
        ChatResult result = bridge.sendChat("hello");

        assertTrue(result.isSuccess());
        assertEquals("sent hello", result.getMessage());
        assertNotEquals(Thread.currentThread(), bridge.ranOn.get());
    }

    @Test
    void reportsFailureInsteadOfPropagating() {
        TestBridge bridge = new TestBridge(new FakeClientThread());
        ChatResult result = bridge.sendChat("boom");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("failed"));
    }

    @Test
    void reportsFailureWhenSchedulingIsRejected() {
        TestBridge bridge = new TestBridge(command -> {
            throw new java.util.concurrent.RejectedExecutionException("shutting down");
        });
        ChatResult result = bridge.sendChat("hello");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Could not schedule"));
    }
}
