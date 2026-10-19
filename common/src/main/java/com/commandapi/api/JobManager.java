package com.commandapi.api;
import com.commandapi.config.ApiConfig;
import com.commandapi.functions.FunctionCompiler;
import com.commandapi.functions.FunctionDefinition;
import com.commandapi.functions.FunctionException;
import com.commandapi.functions.FunctionExecutionPlan;
import com.commandapi.functions.FunctionExecutor;
import com.commandapi.functions.FunctionLimits;
import com.commandapi.functions.FunctionRegistry;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
/** HTTP-facing job registry delegates lifecycle and resources to the shared scheduler. */
final class JobManager implements AutoCloseable {
    private final CommandScheduler scheduler;
    private final ApiConfig config;
    private final MinecraftBridge bridge;
    private FunctionRegistry functions = new FunctionRegistry();
    private FunctionLimits functionLimits = FunctionLimits.defaults();
    private String minecraftVersion = "unknown";
    private FunctionExecutor functionExecutor;
    private ExecutorService functionWorkers;
    private final Map<String, FunctionExecutor.Work> functionJobs = new LinkedHashMap<String, FunctionExecutor.Work>();
    private final AtomicInteger queuedFunctionCommands = new AtomicInteger();
    JobManager(MinecraftBridge bridge) { this(bridge, ApiConfig.defaults()); }
    JobManager(MinecraftBridge bridge, ApiConfig config) {
        this.bridge = bridge;
        this.config = config;
        scheduler = new CommandScheduler(bridge, config);
        functionExecutor = new FunctionExecutor(bridge, config);
        functionWorkers = Executors.newFixedThreadPool(Math.max(1, config.getJobConcurrency()),
                new java.util.concurrent.ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger();
                    @Override public Thread newThread(Runnable task) {
                        Thread thread = new Thread(task,
                                "commandapi-functions-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                });
    }
    JsonObject submit(ApiRequest request) {
        CommandScheduler.Work work = scheduler.submit(request.getMessages()); return work == null ? null : work.snapshot();
    }
    CommandScheduler.Work execute(ApiRequest request) { return scheduler.submit(request.getMessages()); }
    JsonObject status() { return scheduler.status(); }
    JsonObject get(String id, boolean cancel) {
        CommandScheduler.Work work = scheduler.find(id); if (work == null) {
            FunctionExecutor.Work fn = findFunction(id);
            if (fn == null) return null;
            if (cancel) fn.cancel("CLIENT_CANCELLED"); return fn.snapshot();
        }
        if (cancel) work.cancel("CLIENT_CANCELLED"); return work.snapshot();
    }
    @Override public void close() {
        for (FunctionExecutor.Work work : copyFunctionJobs()) work.cancel("SHUTDOWN");
        if (functionWorkers != null) functionWorkers.shutdownNow();
        scheduler.close();
    }

    // -- Functions ---------------------------------------------------------
    void setFunctions(FunctionRegistry registry, FunctionLimits limits, String version) {
        if (registry != null) this.functions = registry;
        if (limits != null) this.functionLimits = limits;
        if (version != null) this.minecraftVersion = version;
        this.functionExecutor = new FunctionExecutor(bridge, config);
    }

    FunctionRegistry functions() { return functions; }
    FunctionLimits functionLimits() { return functionLimits; }

    FunctionExecutionPlan preview(String id, Map<String, Object> args) throws FunctionException {
        FunctionCompiler compiler = new FunctionCompiler(functions, functionLimits,
                config.getCommandPolicy(), minecraftVersion);
        return compiler.compile(id, args);
    }

    /** Synchronous run: executes on the calling thread, reusing transport/session semantics. */
    FunctionExecutor.Work runFunctionSync(String id, Map<String, Object> args)
            throws FunctionException {
        FunctionExecutionPlan plan = preview(id, args);
        if (!bridge.isInWorld()) throw new FunctionException("Session unavailable", "NO_SESSION");
        Object session = bridge.sessionIdentity();
        if (session == null) throw new FunctionException("Session unavailable", "NO_SESSION");
        if (!reserveFunctionCapacity(plan.commands().size())) return null;
        FunctionExecutor.Work work;
        try {
            work = functionExecutor.create(plan);
            trackFunction(work);
            work.run();
        } finally {
            releaseFunctionCapacity(plan.commands().size());
        }
        return work;
    }

    /** Async run: queued on the function worker pool; FIFO per job, capacity-bounded. */
    FunctionExecutor.Work submitFunction(String id, Map<String, Object> args)
            throws FunctionException {
        FunctionExecutionPlan plan = preview(id, args);
        if (!bridge.isInWorld()) throw new FunctionException("Session unavailable", "NO_SESSION");
        if (!reserveFunctionCapacity(plan.commands().size())) return null;
        final FunctionExecutor.Work work = functionExecutor.create(plan);
        trackFunction(work);
        final int commands = plan.commands().size();
        try {
            functionWorkers.execute(new Runnable() {
                @Override public void run() {
                    try { work.run(); }
                    finally { releaseFunctionCapacity(commands); }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            untrackFunction(work.id());
            releaseFunctionCapacity(commands);
            return null;
        }
        return work;
    }

    synchronized void trackFunction(FunctionExecutor.Work work) {
        functionJobs.put(work.id(), work);
    }

    synchronized void untrackFunction(String id) { functionJobs.remove(id); }

    synchronized FunctionExecutor.Work findFunction(String id) { return functionJobs.get(id); }

    private synchronized java.util.List<FunctionExecutor.Work> copyFunctionJobs() {
        return new java.util.ArrayList<FunctionExecutor.Work>(functionJobs.values());
    }

    private boolean reserveFunctionCapacity(int commands) {
        int maxJobs = config.getMaxJobs();
        synchronized (this) {
            if (functionJobs.size() >= maxJobs * 2) return false;
        }
        while (true) {
            int current = queuedFunctionCommands.get();
            if (current + commands > config.getMaxQueuedCommands()) return false;
            if (queuedFunctionCommands.compareAndSet(current, current + commands)) return true;
        }
    }

    private void releaseFunctionCapacity(int commands) {
        queuedFunctionCommands.addAndGet(-commands);
    }
}
