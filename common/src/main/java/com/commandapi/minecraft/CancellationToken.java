package com.commandapi.minecraft;
import java.util.concurrent.atomic.AtomicBoolean;
/** Cooperative cancellation. Never interrupts a Minecraft dispatcher. */
public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    public boolean isCancelled() { return cancelled.get(); }
    public void cancel() { cancelled.set(true); }
}
