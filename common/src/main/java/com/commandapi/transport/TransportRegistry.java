package com.commandapi.transport;
import com.commandapi.minecraft.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
/** One selector for all versions. Fallback happens only before any EXECUTE is sent. */
public final class TransportRegistry {
    private final CommandTransport nativeTransport, vanilla;
    private final ServerPluginTransport plugin;
    public TransportRegistry(CommandTransport nativeTransport, ServerPluginTransport plugin, CommandTransport vanilla) {
        this.nativeTransport = nativeTransport; this.plugin = plugin; this.vanilla = vanilla;
    }
    public String selected(boolean integrated) { return integrated ? nativeTransport.name() : plugin.available() ? plugin.name() : vanilla.name(); }
    public CompletableFuture<ChatResult> execute(boolean integrated, String text, CancellationToken token, Supplier<Boolean> sameSession) {
        if (!text.startsWith("/")) return vanilla.execute(text, token);
        if (integrated) return nativeTransport.execute(text, token);
        if (plugin.negotiating()) return plugin.negotiation().thenCompose(ready -> {
            if (token.isCancelled() || !sameSession.get()) return CompletableFuture.completedFuture(ChatResult.failure("Session changed before dispatch", "SESSION_CHANGED"));
            return ready ? plugin.execute(text, token) : vanilla.execute(text, token);
        });
        return plugin.available() ? plugin.execute(text, token) : vanilla.execute(text, token);
    }
}
