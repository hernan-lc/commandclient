package com.commandapi.functions;

import com.commandapi.api.ApiResponse;
import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.CancellationToken;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes a compiled {@link FunctionExecutionPlan} command by command
 * through the active {@link MinecraftBridge} transport.
 *
 * <p>Mirrors {@code CommandScheduler.Task} semantics: FIFO order, session
 * affinity, per-command timeout, stop on unknown outcomes (never auto-retry
 * non-idempotent commands such as {@code summon}), and cooperative
 * cancellation (already-dispatched commands cannot be undone).</p>
 *
 * <p>Tick delays never block the Minecraft game thread: the executor runs on
 * an HTTP worker thread and waits in small interruptible slices, holding no
 * queue capacity while waiting. Strict per-job FIFO order is preserved
 * because one worker drives the whole plan sequentially.</p>
 */
public final class FunctionExecutor {
    /** Milliseconds per game tick used to realize tick delays off-thread. */
    public static final long MILLIS_PER_TICK = 50L;

    private static final Gson GSON = new Gson();

    /** Cancellable tick wait, overridable in tests. */
    public interface DelayService {
        /** Waits {@code ticks} game ticks; returns false when cancelled early. */
        boolean waitTicks(int ticks, CancellationToken token, Object session,
                SessionCheck check) throws InterruptedException;
    }

    /** Session liveness probe for long delays. */
    public interface SessionCheck {
        boolean sameSession(Object session);
    }

    private final MinecraftBridge bridge;
    private final ApiConfig config;
    private final DelayService delays;

    public FunctionExecutor(MinecraftBridge bridge, ApiConfig config) {
        this(bridge, config, defaultDelays());
    }

    public FunctionExecutor(MinecraftBridge bridge, ApiConfig config, DelayService delays) {
        this.bridge = bridge;
        this.config = config;
        this.delays = delays;
    }

    /** Runs the plan to completion on the calling thread. */
    public Work run(FunctionExecutionPlan plan) {
        Work work = new Work(plan.getFunctionId(), plan);
        work.run();
        return work;
    }

    /** Creates a cancellable run handle without starting it. */
    public Work create(FunctionExecutionPlan plan) {
        return new Work(plan.getFunctionId(), plan);
    }

    public final class Work {
        private final String id = UUID.randomUUID().toString();
        private final String functionId;
        private final FunctionExecutionPlan plan;
        private final CancellationToken token = new CancellationToken();
        private final List<JsonObject> results = new ArrayList<JsonObject>();
        private final CompletableFuture<Void> completion = new CompletableFuture<Void>();
        private volatile String state = "queued";
        private volatile String lastError;
        private volatile int failures;
        private volatile long finishedAt;
        private final Object session;

        Work(String functionId, FunctionExecutionPlan plan) {
            this.functionId = functionId;
            this.plan = plan;
            Object captured;
            try {
                captured = bridge.sessionIdentity();
            } catch (RuntimeException e) {
                captured = null;
            }
            this.session = captured;
        }

        public String id() {
            return id;
        }

        public String function() {
            return functionId;
        }

        public synchronized boolean finished() {
            return finishedAt != 0;
        }

        public void run() {
            synchronized (this) {
                if (finished()) {
                    return;
                }
                state = "running";
            }
            long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(config.getExecutionTimeoutSeconds()
                            * Math.max(1, Math.max(1, plan.getItems().size())));
            try {
                for (FunctionExecutionPlan.Item item : plan.getItems()) {
                    if (token.isCancelled()) {
                        break;
                    }
                    if (System.nanoTime() > deadline) {
                        addUnknown("Function execution timeout; do not retry automatically",
                                "EXECUTION_TIMEOUT");
                        token.cancel();
                        break;
                    }
                    if (item instanceof FunctionExecutionPlan.Delay) {
                        int ticks = ((FunctionExecutionPlan.Delay) item).getTicks();
                        try {
                            boolean waited = delays.waitTicks(ticks, token, session,
                                    new SessionCheck() {
                                        @Override
                                        public boolean sameSession(Object session) {
                                            return Objects.equals(session,
                                                    bridge.sessionIdentity());
                                        }
                                    });
                            if (!waited) {
                                break;
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            cancel("DELAY_INTERRUPTED");
                            break;
                        }
                        if (!Objects.equals(session, bridge.sessionIdentity())) {
                            cancel("SESSION_CHANGED");
                            break;
                        }
                        continue;
                    }
                    String command =
                            ((FunctionExecutionPlan.Command) item).getCommand();
                    if (!Objects.equals(session, bridge.sessionIdentity())) {
                        cancel("SESSION_CHANGED");
                        break;
                    }
                    ChatResult result;
                    try {
                        CompletableFuture<ChatResult> pending =
                                bridge.submitChat(command, session, token);
                        result = pending.get(config.getExecutionTimeoutSeconds(),
                                TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        token.cancel();
                        result = ChatResult.unknown(
                                "Execution timeout; do not retry automatically",
                                "EXECUTION_TIMEOUT");
                    } catch (InterruptedException e) {
                        token.cancel();
                        Thread.currentThread().interrupt();
                        result = ChatResult.unknown(
                                "Shutdown interrupted the wait; execution may finish",
                                "WAIT_CANCELLED");
                    } catch (Throwable e) {
                        result = ChatResult.failure("Transport failed", "TRANSPORT_FAILED");
                    }
                    synchronized (this) {
                        results.add(ApiResponse.chatResult(command, result));
                        if (!result.isSuccess()) {
                            failures++;
                            lastError = result.getCode();
                        }
                    }
                    if ("unknown".equals(result.getStage())) {
                        break;
                    }
                    if (!result.isSuccess() && stopOnError()) {
                        break;
                    }
                }
            } finally {
                finish();
            }
        }

        private boolean stopOnError() {
            // Default: keep going on confirmed failures (matches /api/chat),
            // always stop on unknown outcomes. A strict stop-on-error mode can
            // be layered per call site later without changing this default.
            return false;
        }

        private void addUnknown(String message, String code) {
            synchronized (this) {
                JsonObject json = new JsonObject();
                json.addProperty("text", "");
                json.addProperty("success", false);
                json.addProperty("output", message);
                json.addProperty("execution_status", "unknown");
                json.addProperty("code", code);
                results.add(json);
                failures++;
                lastError = code;
            }
        }

        synchronized void finish() {
            if (finished()) {
                return;
            }
            if ("cancelled".equals(state)) {
                // keep cancellation state
            } else if (token.isCancelled()) {
                state = "cancelled";
                if (lastError == null) {
                    lastError = "CANCELLED";
                }
            } else {
                state = failures == 0 ? "completed" : "failed";
            }
            finishedAt = System.nanoTime();
            completion.complete(null);
        }

        public void cancel(String reason) {
            synchronized (this) {
                if (finished()) {
                    return;
                }
                token.cancel();
                state = "cancelled";
                lastError = reason;
                finishedAt = System.nanoTime();
            }
            completion.complete(null);
        }

        public void await(int seconds) throws InterruptedException,
                java.util.concurrent.ExecutionException, TimeoutException {
            completion.get(seconds, TimeUnit.SECONDS);
        }

        public synchronized JsonObject snapshot() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("function", functionId);
            json.addProperty("state", state);
            json.addProperty("total_steps", plan.getItems().size());
            json.addProperty("processed_steps", results.size());
            // Backwards-compatible aliases mirroring job snapshots.
            json.addProperty("total", plan.getItems().size());
            json.addProperty("processed", results.size());
            json.addProperty("failed_commands", failures);
            if (lastError != null) {
                json.addProperty("last_error", lastError);
            }
            com.google.gson.JsonArray array = new com.google.gson.JsonArray();
            for (JsonObject result : results) {
                array.add(GSON.fromJson(result.toString(), JsonObject.class));
            }
            json.add("results", array);
            return json;
        }

        public CancellationToken token() {
            return token;
        }

        public FunctionExecutionPlan plan() {
            return plan;
        }
    }

    private static DelayService defaultDelays() {
        return new DelayService() {
            @Override
            public boolean waitTicks(int ticks, CancellationToken token, Object session,
                    SessionCheck check) throws InterruptedException {
                long totalMillis = ticks * MILLIS_PER_TICK;
                long waited = 0;
                while (waited < totalMillis) {
                    if (token.isCancelled()) {
                        return false;
                    }
                    long slice = Math.min(10L, totalMillis - waited);
                    Thread.sleep(slice);
                    waited += slice;
                }
                return !token.isCancelled();
            }
        };
    }
}
