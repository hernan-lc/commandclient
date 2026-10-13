package com.commandapi.api;

import com.commandapi.minecraft.*;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JobManagerTest {
    @Test void jobsReturnBeforeExecutionAndPreserveOrder() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        MinecraftBridge bridge = new MinecraftBridge() {
            public boolean isInWorld() { return true; }
            public String getPlayerName() { return "player"; }
            public ChatResult sendChat(String text) {
                started.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                sent.add(text);
                return ChatResult.ok("sent");
            }
        };
        try (JobManager jobs = new JobManager(bridge)) {
            String id = jobs.submit(ApiRequest.parse("{\"messages\":[\"/first\",\"/second\"]}")).get("id").getAsString();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("running", jobs.get(id, false).get("state").getAsString());
            release.countDown();
            awaitState(jobs, id, "completed");
            assertEquals(Arrays.asList("/first", "/second"), sent);
        } finally { release.countDown(); }
    }
    @Test void cancellationPreventsRemainingCommands() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        MinecraftBridge bridge = new MinecraftBridge() {
            public boolean isInWorld() { return true; }
            public String getPlayerName() { return "player"; }
            public ChatResult sendChat(String text) {
                sent.add(text); started.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ChatResult.ok("sent");
            }
        };
        try (JobManager jobs = new JobManager(bridge)) {
            String id = jobs.submit(ApiRequest.parse("{\"messages\":[\"/first\",\"/second\"]}")).get("id").getAsString();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("cancelled", jobs.get(id, true).get("state").getAsString());
            release.countDown();
            assertEquals(Collections.singletonList("/first"), sent);
        } finally { release.countDown(); }
    }
    @Test void queuedJobsCannotEnterAnotherSession() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final Object[] session = {new Object()};
        MinecraftBridge bridge = new MinecraftBridge() {
            public boolean isInWorld() { return true; }
            public String getPlayerName() { return "same-player"; }
            public Object sessionIdentity() { return session[0]; }
            public ChatResult sendChat(String text) {
                entered.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return ChatResult.ok("sent");
            }
        };
        try (JobManager jobs = new JobManager(bridge)) {
            jobs.submit(ApiRequest.parse("{\"text\":\"/blocking\"}"));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            String id = jobs.submit(ApiRequest.parse("{\"text\":\"/queued\"}")).get("id").getAsString();
            session[0] = new Object(); release.countDown();
            awaitState(jobs, id, "cancelled");
            assertEquals(0, jobs.get(id, false).get("processed").getAsInt());
        } finally { release.countDown(); }
    }
    static void awaitState(JobManager jobs, String id, String state) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (state.equals(jobs.get(id, false).get("state").getAsString())) return;
            Thread.sleep(5);
        }
        fail("Job did not reach " + state);
    }
}
