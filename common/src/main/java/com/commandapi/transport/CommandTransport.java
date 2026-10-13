package com.commandapi.transport;
import com.commandapi.minecraft.*;
import java.util.concurrent.CompletableFuture;
public interface CommandTransport {
    String name();
    CompletableFuture<ChatResult> execute(String text, CancellationToken cancellation);
}
