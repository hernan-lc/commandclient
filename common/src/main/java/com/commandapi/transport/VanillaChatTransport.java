package com.commandapi.transport;
import com.commandapi.minecraft.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
public final class VanillaChatTransport implements CommandTransport {
    private final Function<String, ChatResult> sender;
    public VanillaChatTransport(Function<String, ChatResult> sender) { this.sender = sender; }
    public String name() { return "client-chat"; }
    public CompletableFuture<ChatResult> execute(String text, CancellationToken token) {
        return CompletableFuture.completedFuture(token.isCancelled() ? ChatResult.failure("Cancelled", "CANCELLED") : sender.apply(text));
    }
}
