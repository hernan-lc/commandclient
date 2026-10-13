package com.commandapi.server;
import com.commandapi.minecraft.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.Plugin;
import java.util.concurrent.*;

/** Owns tick dispatch. Bukkit callbacks and permissions are evaluated only on the main thread. */
final class BukkitCommandExecutor implements MinecraftBridge, ServerCommandExecutor {
    private final Plugin plugin;
    private final ServerSettings settings;
    private final TickCommandQueue queue = new TickCommandQueue();
    private volatile boolean closed;
    BukkitCommandExecutor(Plugin plugin, ServerSettings settings) { this.plugin = plugin; this.settings = settings; }
    private CommandSender sender() { return settings.console ? Bukkit.getConsoleSender() : settings.selectedPlayer == null ? null : Bukkit.getPlayer(settings.selectedPlayer); }
    private <T> T onMain(Callable<T> action, T unavailable) {
        if (closed) return unavailable;
        if (Bukkit.isPrimaryThread()) { try { return action.call(); } catch (Exception e) { return unavailable; } }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || future.isDone()) return;
                try { future.complete(action.call()); } catch (Exception e) { future.complete(unavailable); }
            });
            return future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) { future.cancel(false); Thread.currentThread().interrupt(); return unavailable; }
        catch (Exception e) { future.cancel(false); return unavailable; }
    }
    @Override public boolean isInWorld() { return onMain(() -> sender() != null, false); }
    @Override public String getPlayerName() { return onMain(() -> sender() == null ? null : sender().getName(), null); }
    @Override public Object sessionIdentity() { return onMain(() -> sender(), null); }
    @Override public com.google.gson.JsonObject capabilities() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("protocol_version", com.commandapi.protocol.BridgeProtocol.VERSION);
        json.addProperty("async_jobs", true); json.addProperty("console", settings.console); json.addProperty("tick_queue_depth", queue.size()); return json;
    }
    @Override public String executionMode() { return "server-native-" + (settings.console ? "console" : "player"); }
    @Override public ChatResult sendChat(String text) {
        if (Bukkit.isPrimaryThread()) return ChatResult.failure("Use asynchronous submission on the server thread", "THREAD_OWNERSHIP");
        try { return submitChat(text, sessionIdentity(), new CancellationToken()).get(settings.api.getExecutionTimeoutSeconds(), TimeUnit.SECONDS); }
        catch (Exception e) { return ChatResult.unknown("Execution completion unavailable", "OUTCOME_UNKNOWN"); }
    }
    @Override public CompletableFuture<ChatResult> submitChat(String text, Object session, CancellationToken token) {
        if (closed) return CompletableFuture.completedFuture(ChatResult.failure("Plugin disabled", "SHUTDOWN"));
        return queue.submit(this, token, () -> {
            CommandSender current = sender();
            if (current == null || current != session) return ChatResult.failure("Selected player unavailable", "SESSION_CHANGED");
            return dispatch(current, text, false);
        }, settings.api.getMaxQueuedCommands());
    }
    @Override public boolean authorized(Player player) {
        return !closed && player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                && player.hasPermission("commandapi.execute")
                && (settings.players.isEmpty() || settings.players.contains(player.getUniqueId()));
    }
    @Override public CompletableFuture<ChatResult> executePlayer(Player player, String text, CancellationToken token) {
        // No console branch is reachable through this method.
        return queue.submit(this, token, () -> {
            if (!authorized(player)) return ChatResult.failure("Player not authorized or session ended", "PLAYER_DENIED");
            return dispatch(player, text, true);
        }, settings.api.getMaxQueuedCommands());
    }
    private ChatResult dispatch(CommandSender sender, String text, boolean connectedClient) {
        if (!Bukkit.isPrimaryThread()) return ChatResult.failure("Server thread required", "THREAD_OWNERSHIP");
        if (!text.startsWith("/") || !settings.policy.allows(text)) return ChatResult.failure("Command denied by server policy", "COMMAND_DENIED");
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if ((!settings.players.isEmpty() && !settings.players.contains(player.getUniqueId())) || (connectedClient && !authorized(player)))
                return ChatResult.failure("Player denied", "PLAYER_DENIED");
            PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, text);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return ChatResult.failure("Command cancelled by server policy", "COMMAND_CANCELLED");
            text = event.getMessage();
        } else {
            ServerCommandEvent event = new ServerCommandEvent(sender, text.substring(1));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return ChatResult.failure("Console command cancelled", "COMMAND_CANCELLED");
            text = "/" + event.getCommand();
        }
        if (!text.startsWith("/") || !settings.policy.allows(text)) return ChatResult.failure("Rewritten command denied", "COMMAND_DENIED");
        ChatResult result;
        try { result = Bukkit.dispatchCommand(sender, text.substring(1)) ? ChatResult.ok("Command dispatched; plugin outcome is not confirmed")
                    : ChatResult.failure("Command not dispatched", "COMMAND_REJECTED"); }
        catch (Exception e) { result = ChatResult.failure("Command dispatcher exception", "DISPATCH_EXCEPTION"); }
        String identity = sender instanceof Player ? ((Player) sender).getUniqueId().toString() : "console";
        plugin.getLogger().info("CommandAPI audit sender=" + identity + " outcome=" + result.getCode());
        return result;
    }
    @Override public void tick() {
        // HTTP sessions and connected-client sessions share a single global per-tick budget.
        queue.drain(this, settings.api.getCommandsPerTick(), 2000000L);
    }
    @Override public void close() { closed = true; queue.close(); }
}
