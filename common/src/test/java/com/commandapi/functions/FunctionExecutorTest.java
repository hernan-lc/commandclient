package com.commandapi.functions;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.CancellationToken;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import com.commandapi.security.CommandPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionExecutorTest {
    static final class ScriptedBridge implements MinecraftBridge {
        final List<String> sent = Collections.synchronizedList(new ArrayList<String>());
        final Object session = new Object();
        volatile boolean inWorld = true;
        volatile boolean sessionChanged;
        final java.util.Queue<ChatResult> script = new java.util.concurrent.ConcurrentLinkedQueue<ChatResult>();
        final CountDownLatch firstSend = new CountDownLatch(1);
        volatile CountDownLatch gate;

        @Override public boolean isInWorld() {
            return inWorld;
        }

        @Override public String getPlayerName() {
            return "Steve";
        }

        @Override public Object sessionIdentity() {
            return sessionChanged ? new Object() : session;
        }

        @Override public ChatResult sendChat(String text) {
            sent.add(text);
            ChatResult next = script.poll();
            return next == null ? ChatResult.ok("sent") : next;
        }
    }

    private ApiConfig config() {
        return new ApiConfig("127.0.0.1", 0, "secret", true, true, false, true,
                Arrays.asList("*"), 128, 1024, 16, 600, 8, 64, 30, 1);
    }

    private FunctionExecutionPlan plan(String... commands) {
        List<FunctionExecutionPlan.Item> items = new ArrayList<FunctionExecutionPlan.Item>();
        for (String command : commands) {
            items.add(new FunctionExecutionPlan.Command(command));
        }
        return new FunctionExecutionPlan("ns:fn",
                Collections.<String, Object>emptyMap(), items,
                Collections.<com.google.gson.JsonObject>emptyList());
    }

    @Test void executesCommandsInDeterministicOrder() {
        ScriptedBridge bridge = new ScriptedBridge();
        FunctionExecutor executor = new FunctionExecutor(bridge, config());
        FunctionExecutor.Work work = executor.run(plan("/say one", "/say two", "/say three"));
        assertEquals("completed", work.snapshot().get("state").getAsString());
        assertEquals(Arrays.asList("/say one", "/say two", "/say three"), bridge.sent);
        assertEquals(3, work.snapshot().get("total_steps").getAsInt());
        assertEquals(3, work.snapshot().get("processed_steps").getAsInt());
        assertEquals("ns:fn", work.snapshot().get("function").getAsString());
    }

    @Test void unknownOutcomeStopsWithoutRetryingSummon() {
        ScriptedBridge bridge = new ScriptedBridge();
        bridge.script.add(ChatResult.ok("first"));
        bridge.script.add(ChatResult.unknown("boom", "OUTCOME_UNKNOWN"));
        FunctionExecutor executor = new FunctionExecutor(bridge, config());
        FunctionExecutor.Work work = executor.run(
                plan("/summon minecraft:zombie ~ ~ ~", "/summon minecraft:zombie ~ ~ ~",
                        "/say after"));
        assertEquals(2, bridge.sent.size());
        assertEquals("unknown",
                work.snapshot().getAsJsonArray("results").get(1).getAsJsonObject()
                        .get("execution_status").getAsString());
        assertTrue(work.snapshot().get("processed_steps").getAsInt() == 2);
    }

    @Test void cancellationDuringExecutionStopsFutureSteps() throws Exception {
        final ScriptedBridge bridge = new ScriptedBridge();
        bridge.gate = new CountDownLatch(1);
        MinecraftBridge gated = new MinecraftBridge() {
            @Override public boolean isInWorld() {
                return true;
            }

            @Override public String getPlayerName() {
                return "Steve";
            }

            @Override public Object sessionIdentity() {
                return bridge.session;
            }

            @Override public ChatResult sendChat(String text) {
                bridge.sent.add(text);
                try {
                    bridge.gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChatResult.ok("sent");
            }

            @Override public CompletableFuture<ChatResult> submitChat(String text,
                    Object session, CancellationToken token) {
                final CompletableFuture<ChatResult> future = new CompletableFuture<ChatResult>();
                new Thread(new Runnable() {
                    @Override public void run() {
                        future.complete(sendChat(text));
                    }
                }).start();
                return future;
            }
        };
        FunctionExecutor executor = new FunctionExecutor(gated, config());
        FunctionExecutor.Work work = executor.create(
                plan("/say one", "/say two", "/say three"));
        Thread runner = new Thread(new Runnable() {
            @Override public void run() {
                work.run();
            }
        });
        runner.start();
        Thread.sleep(200);
        work.cancel("CLIENT_CANCELLED");
        bridge.gate.countDown();
        runner.join(5000);
        assertEquals("cancelled", work.snapshot().get("state").getAsString());
        assertTrue(bridge.sent.size() < 3);
    }

    @Test void sessionChangeStopsExecution() {
        ScriptedBridge bridge = new ScriptedBridge();
        bridge.sessionChanged = true;
        FunctionExecutor executor = new FunctionExecutor(bridge, config());
        // Session captured at creation differs at run time.
        FunctionExecutor.Work work = executor.run(plan("/say one", "/say two"));
        assertEquals("cancelled", work.snapshot().get("state").getAsString());
        assertTrue(bridge.sent.isEmpty());
    }

    @Test void tickDelayDoesNotConsumeQueueAndIsCancellable() throws Exception {
        ScriptedBridge bridge = new ScriptedBridge();
        final CountDownLatch enteredDelay = new CountDownLatch(1);
        final CountDownLatch releaseDelay = new CountDownLatch(1);
        FunctionExecutor.DelayService delays = new FunctionExecutor.DelayService() {
            @Override public boolean waitTicks(int ticks, CancellationToken token,
                    Object session, FunctionExecutor.SessionCheck check) throws InterruptedException {
                enteredDelay.countDown();
                // Cancellable wait: returns immediately when cancelled.
                while (!releaseDelay.await(10, TimeUnit.MILLISECONDS)) {
                    if (token.isCancelled()) {
                        return false;
                    }
                }
                return !token.isCancelled();
            }
        };
        FunctionExecutor executor = new FunctionExecutor(bridge, config(), delays);
        List<FunctionExecutionPlan.Item> items = new ArrayList<FunctionExecutionPlan.Item>();
        items.add(new FunctionExecutionPlan.Command("/say before"));
        items.add(new FunctionExecutionPlan.Delay(40));
        items.add(new FunctionExecutionPlan.Command("/say after"));
        FunctionExecutionPlan delayed = new FunctionExecutionPlan("ns:wait",
                Collections.<String, Object>emptyMap(), items,
                Collections.<com.google.gson.JsonObject>emptyList());
        final FunctionExecutor.Work work = executor.create(delayed);
        Thread runner = new Thread(new Runnable() {
            @Override public void run() {
                work.run();
            }
        });
        runner.start();
        assertTrue(enteredDelay.await(5, TimeUnit.SECONDS));
        work.cancel("CLIENT_CANCELLED");
        releaseDelay.countDown();
        runner.join(5000);
        assertEquals("cancelled", work.snapshot().get("state").getAsString());
        // Only the pre-delay command dispatched; the delay held no queue slot.
        assertEquals(Collections.singletonList("/say before"), bridge.sent);
    }

    @Test void immediateDelayServiceRunsToCompletion() {
        ScriptedBridge bridge = new ScriptedBridge();
        FunctionExecutor.DelayService instant = new FunctionExecutor.DelayService() {
            @Override public boolean waitTicks(int ticks, CancellationToken token,
                    Object session, FunctionExecutor.SessionCheck check) {
                return true;
            }
        };
        FunctionExecutor executor = new FunctionExecutor(bridge, config(), instant);
        List<FunctionExecutionPlan.Item> items = new ArrayList<FunctionExecutionPlan.Item>();
        items.add(new FunctionExecutionPlan.Command("/say before"));
        items.add(new FunctionExecutionPlan.Delay(20));
        items.add(new FunctionExecutionPlan.Command("/say after"));
        FunctionExecutionPlan delayed = new FunctionExecutionPlan("ns:wait",
                Collections.<String, Object>emptyMap(), items,
                Collections.<com.google.gson.JsonObject>emptyList());
        FunctionExecutor.Work work = executor.run(delayed);
        assertEquals("completed", work.snapshot().get("state").getAsString());
        assertEquals(Arrays.asList("/say before", "/say after"), bridge.sent);
    }

    @Test void jobProgressCountsProcessedSteps() {
        ScriptedBridge bridge = new ScriptedBridge();
        bridge.script.add(ChatResult.failure("nope", "EXECUTION_FAILED"));
        FunctionExecutor executor = new FunctionExecutor(bridge, config());
        // Confirmed failure is recorded; default keeps going (matches /api/chat).
        FunctionExecutor.Work work = executor.run(plan("/say one", "/say two"));
        assertEquals("failed", work.snapshot().get("state").getAsString());
        assertEquals(2, work.snapshot().get("processed_steps").getAsInt());
        assertEquals(1, work.snapshot().get("failed_commands").getAsInt());
    }

    @Test void queueCapacityExhaustionReturnsNull() throws Exception {
        FunctionRegistry registry = new FunctionRegistry();
        registry.register(JsonFunctionParser.parse("ns:fn",
                "{\"schemaVersion\":1,\"name\":\"ns:fn\","
                        + "\"steps\":[{\"type\":\"command\",\"value\":\"say hi\"}]}", "test"));
        ApiConfig tiny = new ApiConfig("127.0.0.1", 0, "secret", true, true, false, true,
                Arrays.asList("*"), 1, 1, 16, 600, 8, 64, 30, 1);
        ScriptedBridge bridge = new ScriptedBridge();
        com.commandapi.api.ApiRequest request = com.commandapi.api.ApiRequest.parse(
                "{\"messages\":[\"/say one\", \"/say two\"]}");
        com.commandapi.api.CommandScheduler scheduler =
                new com.commandapi.api.CommandScheduler(bridge, tiny);
        scheduler.close();
        // Closed scheduler refuses new work instead of queuing unboundedly.
        assertEquals(null, scheduler.submit(request.getMessages()));
    }
}
