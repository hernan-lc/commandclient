package com.commandapi.minecraft;

/** Aggregates callbacks without treating a dispatched command as a world-effect guarantee. */
public final class ExecutionFeedback {
    private int callbacks, value;
    private boolean failed;
    public synchronized void record(boolean success, int result) { callbacks++; value += result; failed |= !success; }
    public synchronized ChatResult result() {
        if (callbacks == 0) return ChatResult.unknown("Command dispatched without a confirmed result", "OUTCOME_UNKNOWN");
        return ChatResult.execution(!failed, value, callbacks);
    }
}
