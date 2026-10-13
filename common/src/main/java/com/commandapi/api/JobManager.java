package com.commandapi.api;

import com.commandapi.minecraft.MinecraftBridge;
import com.commandapi.minecraft.ChatResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, ordered jobs. No artificial delay; one worker preserves ordering. */
final class JobManager implements AutoCloseable {
    private final MinecraftBridge bridge;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "commandapi-jobs");
        thread.setDaemon(true);
        return thread;
    });
    private boolean closed;
    JobManager(MinecraftBridge bridge) { this.bridge = bridge; }

    synchronized JsonObject submit(ApiRequest request) {
        if (closed) return null;
        if (jobs.size() >= 128) {
            Iterator<Job> iterator = jobs.values().iterator();
            while (iterator.hasNext()) {
                Job old = iterator.next();
                if (old.finished()) { iterator.remove(); break; }
            }
        }
        if (jobs.size() >= 128) return null;
        Job job = new Job(request.getMessages(), bridge.sessionIdentity());
        jobs.put(job.id, job);
        job.future = worker.submit(() -> run(job));
        return job.snapshot();
    }
    synchronized JsonObject get(String id, boolean cancel) {
        Job job = jobs.get(id);
        if (job == null) return null;
        synchronized (job) {
            if (cancel && !job.finished()) {
                job.state = "cancelled";
                job.future.cancel(true);
            }
            return job.snapshot();
        }
    }
    private void run(Job job) {
        synchronized (job) {
            if (job.finished()) return;
            job.state = "running";
        }
        for (String message : job.messages) {
            synchronized (job) { if (job.finished()) return; }
            ChatResult result;
            try {
                if (job.session == null || !Objects.equals(job.session, bridge.sessionIdentity())) {
                    synchronized (job) { job.state = "cancelled"; }
                    return;
                }
                result = bridge.isInWorld() ? bridge.sendChat(message, job.session) : ChatResult.failure("Session unavailable");
            } catch (Throwable error) { result = ChatResult.failure("Execution failed: " + error); }
            synchronized (job) {
                job.results.add(ApiResponse.chatResult(message, result));
                if (!result.isSuccess()) job.failed = true;
                if (job.finished()) return;
            }
        }
        synchronized (job) { if (!job.finished()) job.state = job.failed ? "failed" : "completed"; }
    }
    @Override public synchronized void close() {
        closed = true;
        for (Job job : jobs.values()) {
            synchronized (job) {
                if (!job.finished()) job.state = "cancelled";
            }
        }
        worker.shutdownNow();
    }
    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final List<String> messages;
        final Object session;
        final JsonArray results = new JsonArray();
        String state = "queued";
        boolean failed;
        Future<?> future;
        Job(List<String> messages, Object session) { this.messages = messages; this.session = session; }
        synchronized boolean finished() {
            return state.equals("completed") || state.equals("failed") || state.equals("cancelled");
        }
        synchronized JsonObject snapshot() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id);
            json.addProperty("state", state);
            json.addProperty("total", messages.size());
            json.addProperty("processed", results.size());
            json.add("results", results.deepCopy());
            return json;
        }
    }
}
