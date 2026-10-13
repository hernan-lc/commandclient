package com.commandapi.api;
import com.commandapi.transport.*;
import com.commandapi.minecraft.*;
import com.commandapi.protocol.BridgeProtocol.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class TransportRegistryTest {
    @Test void selectsNativeThenAuthorizedPluginThenVanilla() {
        AtomicInteger nativeCount = new AtomicInteger(), vanillaCount = new AtomicInteger(); List<Message> sent = new ArrayList<>();
        ServerPluginTransport plugin = new ServerPluginTransport(); Object session = new Object();
        TransportRegistry registry = new TransportRegistry(new SingleplayerNativeTransport((text, token) -> { nativeCount.incrementAndGet(); return CompletableFuture.completedFuture(ChatResult.execution(true, 1, 1)); }),
                plugin, new VanillaChatTransport(text -> { vanillaCount.incrementAndGet(); return ChatResult.ok("sent"); }));
        assertEquals("executed", registry.execute(true, "/time set day", new CancellationToken(), () -> true).join().getStage());
        plugin.connect(session, sent::add, System.nanoTime());
        CompletableFuture<ChatResult> pending = registry.execute(false, "/time set day", new CancellationToken(), () -> true);
        assertFalse(pending.isDone());
        plugin.receive(session, Message.now(Kind.CAPABILITIES, sent.get(0).id, "player"));
        Message execute = sent.get(1); assertEquals(Kind.EXECUTE, execute.kind);
        plugin.receive(session, Message.now(Kind.EXECUTE_RESULT, execute.id, "dispatched|DISPATCHED|true|"));
        assertEquals("dispatched", pending.join().getStage()); assertEquals(1, nativeCount.get()); assertEquals(0, vanillaCount.get());
        plugin.reset(); registry.execute(false, "/seed", new CancellationToken(), () -> true).join(); assertEquals(1, vanillaCount.get());
    }
    @Test void negotiationTimeoutFallsBackButUnknownExecutionNeverRetries() {
        List<Message> sent = new ArrayList<>(); AtomicInteger vanillaCount = new AtomicInteger(); Object session = new Object();
        ServerPluginTransport plugin = new ServerPluginTransport(); TransportRegistry registry = new TransportRegistry(new SingleplayerNativeTransport((text, token) -> CompletableFuture.completedFuture(ChatResult.ok("native"))), plugin,
                new VanillaChatTransport(text -> { vanillaCount.incrementAndGet(); return ChatResult.ok("sent"); }));
        long now = System.nanoTime(); plugin.connect(session, sent::add, now);
        CompletableFuture<ChatResult> pending = registry.execute(false, "/seed", new CancellationToken(), () -> true);
        plugin.tick(now + 3000000000L); assertTrue(pending.join().isSuccess()); assertEquals(1, vanillaCount.get());
        plugin.reset(); plugin.connect(session, sent::add, System.nanoTime());
        plugin.receive(session, Message.now(Kind.CAPABILITIES, sent.get(sent.size()-1).id, "player"));
        CompletableFuture<ChatResult> command = registry.execute(false, "/give @s stone", new CancellationToken(), () -> true);
        plugin.reset(); assertEquals("unknown", command.join().getStage()); assertEquals(1, vanillaCount.get());
    }
    @Test void staleCapabilitiesAndDisconnectCannotDispatchToNewSession() {
        List<Message> sent = new ArrayList<>(); ServerPluginTransport plugin = new ServerPluginTransport(); Object old = new Object();
        plugin.connect(old, sent::add, System.nanoTime()); Message hello = sent.get(0);
        plugin.connect(new Object(), sent::add, System.nanoTime()); plugin.receive(old, Message.now(Kind.CAPABILITIES, hello.id, "player"));
        assertFalse(plugin.available());
    }
}
