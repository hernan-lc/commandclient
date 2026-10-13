package com.commandapi.server;
import com.commandapi.protocol.BridgeProtocol;
import com.commandapi.protocol.BridgeProtocol.*;
import com.commandapi.minecraft.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Connection-authenticated bridge. Tokens, arbitrary player UUIDs and console authority are absent. */
final class PluginMessageBridge implements PluginMessageListener, AutoCloseable {
    private final Plugin plugin; private final ServerCommandExecutor executor;
    private final Map<Player, Session> sessions = new IdentityHashMap<>();
    private boolean closed;
    PluginMessageBridge(Plugin plugin, ServerCommandExecutor executor) { this.plugin = plugin; this.executor = executor; }
    @Override public synchronized void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (closed || !channel.equals(BridgeProtocol.CHANNEL)) return;
        Message message;
        try { message = BridgeProtocol.decode(bytes, System.currentTimeMillis()); }
        catch (IllegalArgumentException error) { return; } // Untrusted/unparseable identifiers are never echoed.
        Session session = sessions.computeIfAbsent(player, ignored -> new Session());
        long now = System.currentTimeMillis();
        if (now - session.window >= 1000) { session.window = now; session.received = 0; }
        if (++session.received > 16) { reply(player, Message.now(Kind.ERROR, message.id, "RATE_LIMITED")); return; }
        if (message.kind == Kind.HELLO) {
            session.negotiated = executor.authorized(player);
            reply(player, Message.now(Kind.CAPABILITIES, message.id, session.negotiated ? "player" : "denied")); return;
        }
        if (!session.negotiated || !executor.authorized(player)) { reply(player, Message.now(Kind.ERROR, message.id, "PLAYER_DENIED")); return; }
        session.requests.entrySet().removeIf(entry -> entry.getValue().result != null && now - entry.getValue().createdAt > 60000);
        Request prior = session.requests.get(message.id);
        if (message.kind == Kind.CANCEL) {
            if (prior != null) prior.cancellation.cancel();
            reply(player, Message.now(Kind.ERROR, message.id, prior == null ? "REQUEST_NOT_FOUND" : "CANCELLED")); return;
        }
        if (message.kind == Kind.JOB_STATUS || (message.kind == Kind.EXECUTE && prior != null)) {
            if (prior == null) reply(player, Message.now(Kind.ERROR, message.id, "REQUEST_NOT_FOUND"));
            else if (prior.result != null) reply(player, Message.now(Kind.EXECUTE_RESULT, message.id, BridgeProtocol.resultBody(prior.result)));
            else reply(player, Message.now(Kind.ERROR, message.id, "IN_PROGRESS"));
            return;
        }
        if (message.kind != Kind.EXECUTE) { reply(player, Message.now(Kind.ERROR, message.id, "UNEXPECTED_MESSAGE")); return; }
        // Retain results beyond the 30-second frame validity window; active requests are never evicted.
        if (session.requests.size() >= 512) { reply(player, Message.now(Kind.ERROR, message.id, "SESSION_CAPACITY")); return; }
        if (++session.commands > 4 && now - session.commandWindow < 1000) { reply(player, Message.now(Kind.ERROR, message.id, "RATE_LIMITED")); return; }
        if (now - session.commandWindow >= 1000) { session.commandWindow = now; session.commands = 1; }
        Request request = new Request(); session.requests.put(message.id, request);
        CompletableFuture<ChatResult> pending = executor.executePlayer(player, message.body, request.cancellation);
        pending.whenComplete((result, error) -> {
            synchronized (PluginMessageBridge.this) {
                if (closed || sessions.get(player) != session) return;
                request.result = error == null ? result : ChatResult.failure("Dispatcher failed", "DISPATCH_EXCEPTION");
                reply(player, Message.now(Kind.EXECUTE_RESULT, message.id, BridgeProtocol.resultBody(request.result)));
            }
        });
    }
    private void reply(Player player, Message message) { if (!closed && player.isOnline()) player.sendPluginMessage(plugin, BridgeProtocol.CHANNEL, BridgeProtocol.encode(message)); }
    synchronized void disconnected(Player player) {
        Session session = sessions.remove(player); if (session != null) session.requests.values().forEach(request -> request.cancellation.cancel());
    }
    @Override public synchronized void close() { closed = true; sessions.values().forEach(session -> session.requests.values().forEach(request -> request.cancellation.cancel())); sessions.clear(); }
    private static final class Session {
        boolean negotiated; long window, commandWindow; int received, commands;
        final Map<UUID, Request> requests = new LinkedHashMap<>();
    }
    private static final class Request { final long createdAt = System.currentTimeMillis(); final CancellationToken cancellation = new CancellationToken(); ChatResult result; }
}
