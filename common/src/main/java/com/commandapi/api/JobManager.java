package com.commandapi.api;
import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.JsonObject;
/** HTTP-facing job registry delegates lifecycle and resources to the shared scheduler. */
final class JobManager implements AutoCloseable {
    private final CommandScheduler scheduler;
    JobManager(MinecraftBridge bridge) { this(bridge, ApiConfig.defaults()); }
    JobManager(MinecraftBridge bridge, ApiConfig config) { scheduler = new CommandScheduler(bridge, config); }
    JsonObject submit(ApiRequest request) {
        CommandScheduler.Work work = scheduler.submit(request.getMessages()); return work == null ? null : work.snapshot();
    }
    CommandScheduler.Work execute(ApiRequest request) { return scheduler.submit(request.getMessages()); }
    JsonObject status() { return scheduler.status(); }
    JsonObject get(String id, boolean cancel) {
        CommandScheduler.Work work = scheduler.find(id); if (work == null) return null;
        if (cancel) work.cancel("CLIENT_CANCELLED"); return work.snapshot();
    }
    @Override public void close() { scheduler.close(); }
}
