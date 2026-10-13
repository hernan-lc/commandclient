package com.commandapi.transport;
import com.commandapi.minecraft.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
public final class SingleplayerNativeTransport implements CommandTransport {
    private final BiFunction<String, CancellationToken, CompletableFuture<ChatResult>> sender;
    public SingleplayerNativeTransport(BiFunction<String, CancellationToken, CompletableFuture<ChatResult>> sender) { this.sender = sender; }
    public String name() { return "singleplayer-native"; }
    public CompletableFuture<ChatResult> execute(String text, CancellationToken token) { return sender.apply(text, token); }
}
