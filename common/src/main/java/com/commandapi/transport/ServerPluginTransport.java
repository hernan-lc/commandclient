package com.commandapi.transport;
import com.commandapi.minecraft.*;
import com.commandapi.protocol.BridgeProtocol;
import com.commandapi.protocol.BridgeProtocol.*;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
/** Client-thread-confined connection negotiation and request correlation. Never retries execution. */
public final class ServerPluginTransport implements CommandTransport {
    private Object session;
    private Consumer<Message> sender;
    private String state = "unavailable", diagnostic = "NOT_CONNECTED";
    private UUID hello;
    private long negotiationDeadline;
    private CompletableFuture<Boolean> negotiation = CompletableFuture.completedFuture(false);
    private final Map<UUID, Pending> pending = new HashMap<>();
    public String name() { return "server-plugin"; }
    public boolean available() { return state.equals("ready"); }
    public boolean negotiating() { return state.equals("negotiating"); }
    public CompletableFuture<Boolean> negotiation() { return negotiation; }
    public void connect(Object connection, Consumer<Message> sender, long nowNanos) {
        if (session == connection) return;
        reset(); session = connection; this.sender = sender;
        if (connection == null) return;
        state = "negotiating"; diagnostic = "NEGOTIATING"; hello = UUID.randomUUID();
        negotiation = new CompletableFuture<>(); negotiationDeadline = nowNanos + 2000000000L;
        try { sender.accept(Message.now(Kind.HELLO, hello, "")); }
        catch (RuntimeException e) { state = "unavailable"; diagnostic = "NEGOTIATION_SEND_FAILED"; negotiation.complete(false); }
    }
    public void receive(Object connection, Message message) {
        if (session != connection) return;
        if (message.kind == Kind.CAPABILITIES && message.id.equals(hello) && negotiating()) {
            state = message.body.equals("player") ? "ready" : "denied";
            diagnostic = available() ? "READY" : "PLAYER_DENIED"; negotiation.complete(available()); return;
        }
        Pending request = pending.get(message.id);
        if (request == null) return;
        if (message.kind == Kind.ERROR && message.body.equals("IN_PROGRESS")) return;
        if (message.kind == Kind.EXECUTE_RESULT || message.kind == Kind.ERROR) {
            pending.remove(message.id);
            try { request.future.complete(message.kind == Kind.EXECUTE_RESULT ? BridgeProtocol.result(message.body) : ChatResult.failure("Server rejected request", message.body)); }
            catch (IllegalArgumentException error) { request.future.complete(ChatResult.unknown("Malformed server result; do not retry", "INVALID_RESULT")); }
        }
    }
    public void tick(long nowNanos) {
        if (negotiating() && nowNanos >= negotiationDeadline) { state = "unavailable"; diagnostic = "NEGOTIATION_TIMEOUT"; negotiation.complete(false); }
        Iterator<Map.Entry<UUID, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next(); Pending request = entry.getValue();
            if (request.token.isCancelled() || nowNanos >= request.deadline) {
                iterator.remove();
                try { sender.accept(Message.now(Kind.CANCEL, entry.getKey(), "")); } catch (RuntimeException ignored) { }
                request.future.complete(ChatResult.unknown("Server completion unavailable; do not retry", "OUTCOME_UNKNOWN"));
            }
        }
    }
    @Override public CompletableFuture<ChatResult> execute(String text, CancellationToken token) {
        if (!available() || pending.size() >= 128 || token.isCancelled()) return CompletableFuture.completedFuture(ChatResult.failure("Server transport unavailable", "TRANSPORT_UNAVAILABLE"));
        UUID id = UUID.randomUUID(); Pending request = new Pending(token); pending.put(id, request);
        try { sender.accept(Message.now(Kind.EXECUTE, id, text)); }
        catch (RuntimeException error) { pending.remove(id); request.future.complete(ChatResult.unknown("Send failed; execution may have reached server", "TRANSPORT_FAILED")); }
        return request.future;
    }
    public void reset() {
        // Publish new state before completing continuations, so none can dispatch to the old connection.
        session = null; sender = null; state = "unavailable"; diagnostic = "NOT_CONNECTED";
        pending.values().forEach(request -> request.future.complete(ChatResult.unknown("Connection ended; do not retry", "SESSION_CHANGED"))); pending.clear();
        negotiation.complete(false);
    }
    public JsonObject capabilities() {
        JsonObject json = new JsonObject(); json.addProperty("protocol_version", BridgeProtocol.VERSION);
        json.addProperty("server_plugin", state); json.addProperty("diagnostic", diagnostic); json.addProperty("console", false);
        json.addProperty("pending_requests", pending.size()); return json;
    }
    private static final class Pending {
        final CancellationToken token; final CompletableFuture<ChatResult> future = new CompletableFuture<>(); final long deadline = System.nanoTime() + 30000000000L;
        Pending(CancellationToken token) { this.token = token; }
    }
}
