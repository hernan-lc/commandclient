package com.commandapi.functions;

/** Execution budgets for function compilation and runs. Immutable. */
public final class FunctionLimits {
    private final int maxExpandedCommands;
    private final int maxLoopIterations;
    private final int maxNestingDepth;
    private final int maxDelayTicks;
    private final int maxExecutionSeconds;
    private final long maxFileBytes;
    private final int maxArgs;

    public FunctionLimits(int maxExpandedCommands, int maxLoopIterations, int maxNestingDepth,
            int maxDelayTicks, int maxExecutionSeconds, long maxFileBytes, int maxArgs) {
        this.maxExpandedCommands = maxExpandedCommands;
        this.maxLoopIterations = maxLoopIterations;
        this.maxNestingDepth = maxNestingDepth;
        this.maxDelayTicks = maxDelayTicks;
        this.maxExecutionSeconds = maxExecutionSeconds;
        this.maxFileBytes = maxFileBytes;
        this.maxArgs = maxArgs;
    }

    public static FunctionLimits defaults() {
        return new FunctionLimits(256, 100, 8, 1200, 120, 256 * 1024, 32);
    }

    public int getMaxExpandedCommands() {
        return maxExpandedCommands;
    }

    public int getMaxLoopIterations() {
        return maxLoopIterations;
    }

    public int getMaxNestingDepth() {
        return maxNestingDepth;
    }

    public int getMaxDelayTicks() {
        return maxDelayTicks;
    }

    public int getMaxExecutionSeconds() {
        return maxExecutionSeconds;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public int getMaxArgs() {
        return maxArgs;
    }
}
