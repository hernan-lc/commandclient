package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared bounded scheduler for sync/async submissions. Minecraft threads never wait for HTTP. */
public final class CommandScheduler implements AutoCloseable {
    private final MinecraftBridge bridge;
    private final ApiConfig config;
    private final ThreadPoolExecutor workers;
    private final LinkedHashMap<String, Work> retained = new LinkedHashMap<>();
    private final AtomicInteger queuedCommands = new AtomicInteger(), active = new AtomicInteger();
    private final ScheduledExecutorService cleanup;
    private boolean closed;
    public CommandScheduler(MinecraftBridge bridge, ApiConfig config) {
        this.bridge = bridge; this.config = config;
        workers = new ThreadPoolExecutor(config.getJobConcurrency(), config.getJobConcurrency(), 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getMaxJobs()), task -> daemon(task, "commandapi-execution"), new ThreadPoolExecutor.AbortPolicy());
        cleanup = Executors.newSingleThreadScheduledExecutor(task -> daemon(task, "commandapi-retention"));
        cleanup.scheduleAtFixedRate(this::expire, 1, 1, TimeUnit.SECONDS);
    }
    private static Thread daemon(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }
    public Work submit(List<String> messages) {
        Object session = bridge.sessionIdentity(); // Never hold registry locks while calling Minecraft.
        if (session == null) throw new IllegalStateException("Session unavailable");
        synchronized (this) {
            expire();
            if (closed) return null;
            if (retained.size() >= config.getMaxJobs()) {
                Iterator<Work> iterator = retained.values().iterator();
                while (iterator.hasNext()) { if (iterator.next().finished()) { iterator.remove(); break; } }
            }
            if (retained.size() >= config.getMaxJobs() || queuedCommands.get() + messages.size() > config.getMaxQueuedCommands()) return null;
            Work work = new Work(messages, session);
            retained.put(work.id, work); queuedCommands.addAndGet(messages.size());
            try { workers.execute(new Task(work)); }
            catch (RejectedExecutionException error) { retained.remove(work.id); queuedCommands.addAndGet(-messages.size()); return null; }
            return work;
        }
    }
    public synchronized Work find(String id) { expire(); return retained.get(id); }
    private synchronized void expire() {
        long cutoff = System.nanoTime() - TimeUnit.SECONDS.toNanos(config.getRetentionSeconds());
        retained.values().removeIf(work -> work.finishedAt() != 0 && work.finishedAt() < cutoff);
    }
    public JsonObject status() {
        JsonObject json = new JsonObject();
        json.addProperty("queue_depth", workers.getQueue().size());
        json.addProperty("queued_commands", queuedCommands.get());
        json.addProperty("active_jobs", active.get());
        json.addProperty("max_jobs", config.getMaxJobs());
        json.addProperty("max_queued_commands", config.getMaxQueuedCommands());
        json.addProperty("job_concurrency", config.getJobConcurrency());
        return json;
    }
    private final class Task implements Runnable {
        private final Work work;
        Task(Work work) { this.work = work; }
        @Override public void run() {
            active.incrementAndGet();
            try {
                if (!work.begin()) return;
                for (String text : work.messages) {
                    if (work.token.isCancelled()) break;
                    if (!Objects.equals(work.session, bridge.sessionIdentity())) { work.cancel("SESSION_CHANGED"); break; }
                    ChatResult result;
                    try {
                        CompletableFuture<ChatResult> pending = bridge.submitChat(text, work.session, work.token);
                        result = pending.get(config.getExecutionTimeoutSeconds(), TimeUnit.SECONDS);
                    } catch (TimeoutException error) {
                        work.token.cancel();
                        result = ChatResult.unknown("Execution timeout; do not retry automatically", "EXECUTION_TIMEOUT");
                    } catch (InterruptedException error) {
                        work.token.cancel(); Thread.currentThread().interrupt();
                        result = ChatResult.unknown("Shutdown interrupted the wait; execution may finish", "WAIT_CANCELLED");
                    } catch (Throwable error) { result = ChatResult.failure("Transport failed", "TRANSPORT_FAILED"); }
                    work.add(text, result);
                    // Outcome unknown: do not dispatch further non-idempotent commands in this submission.
                    if (result.getStage().equals("unknown")) break;
                }
                work.finish();
            } finally { active.decrementAndGet(); queuedCommands.addAndGet(-work.messages.size()); }
        }
        void cancelPending() { work.cancel("SHUTDOWN"); queuedCommands.addAndGet(-work.messages.size()); }
    }
    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true; retained.values().forEach(work -> work.cancel("SHUTDOWN"));
        }
        cleanup.shutdownNow();
        for (Runnable pending : workers.shutdownNow()) ((Task) pending).cancelPending();
    }
    public static final class Work {
        private final String id = UUID.randomUUID().toString();
        private final List<String> messages;
        private final Object session;
        private final CancellationToken token = new CancellationToken();
        private final List<JsonObject> results = new ArrayList<>();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private String state = "queued", lastError;
        private int failures;
        private long finishedAt;
        Work(List<String> messages, Object session) { this.messages = Collections.unmodifiableList(new ArrayList<>(messages)); this.session = session; }
        public String id() { return id; }
        synchronized boolean begin() { if (finished()) return false; state = "running"; return true; }
        public synchronized boolean finished() { return finishedAt != 0; }
        synchronized long finishedAt() { return finishedAt; }
        synchronized void add(String text, ChatResult result) {
            results.add(ApiResponse.chatResult(text, result));
            if (!result.isSuccess()) { failures++; lastError = result.getCode(); }
        }
        synchronized void finish() {
            if (finished()) return;
            state = failures == 0 ? "completed" : "failed";
            finishedAt = System.nanoTime(); completion.complete(null);
        }
        public synchronized void cancel(String reason) {
            if (finished()) return;
            token.cancel(); state = "cancelled"; lastError = reason;
            finishedAt = System.nanoTime(); completion.complete(null);
        }
        public void await(int seconds) throws InterruptedException, ExecutionException, TimeoutException { completion.get(seconds, TimeUnit.SECONDS); }
        public synchronized List<JsonObject> results() {
            List<JsonObject> copy = new ArrayList<>(); results.forEach(value -> copy.add(value.deepCopy())); return copy;
        }
        public synchronized JsonObject snapshot() {
            JsonObject json = new JsonObject(); json.addProperty("id", id); json.addProperty("state", state);
            json.addProperty("total", messages.size()); json.addProperty("processed", results.size());
            json.addProperty("failed_commands", failures); if (lastError != null) json.addProperty("last_error", lastError);
            JsonArray array = new JsonArray(); results.forEach(value -> array.add(value.deepCopy())); json.add("results", array);
            return json;
        }
    }
}
