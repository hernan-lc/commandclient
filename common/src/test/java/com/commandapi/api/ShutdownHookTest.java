package com.commandapi.api;
import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class ShutdownHookTest {
    @Test void shutdownHookCancelsJobsAndReleasesHttpResources() throws Exception {
        Object session = new Object(); CountDownLatch entered = new CountDownLatch(1);
        CompletableFuture<ChatResult> command = new CompletableFuture<>();
        MinecraftBridge bridge = new MinecraftBridge() {
            public boolean isInWorld() { return true; }
            public String getPlayerName() { return "test"; }
            public Object sessionIdentity() { return session; }
            public ChatResult sendChat(String text) { throw new AssertionError(); }
            public CompletableFuture<ChatResult> submitChat(String text, Object identity, CancellationToken cancellation) {
                entered.countDown(); return command;
            }
        };
        HttpServerManager http = new HttpServerManager(new ApiConfig("127.0.0.1", 0, "secret", true), bridge, "test", "test");
        assertTrue(http.start()); int port = http.getPort();
        Field jobsField = HttpServerManager.class.getDeclaredField("jobs"); jobsField.setAccessible(true);
        JobManager jobs = (JobManager)jobsField.get(http);
        String id = jobs.submit(ApiRequest.parse("{\"text\":\"/one\"}")).get("id").getAsString();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        Field hookField = HttpServerManager.class.getDeclaredField("shutdownHook"); hookField.setAccessible(true);
        Thread hook = (Thread)hookField.get(http);
        try {
            hook.run();
            assertFalse(http.isRunning());
            assertEquals("cancelled", jobs.get(id, false).get("state").getAsString());
            assertFalse(command.isCancelled());
            try (ServerSocket rebound = new ServerSocket(port)) { assertEquals(port, rebound.getLocalPort()); }
        } finally { http.stop(); }
    }
}
