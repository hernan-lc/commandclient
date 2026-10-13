package com.commandapi.server;

import com.commandapi.api.HttpServerManager;
import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.ClientThreadBridge;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Paper/Spigot adapter; command work is confined to the Bukkit main thread. */
public final class CommandApiPlugin extends JavaPlugin {
    private HttpServerManager http;
    @Override public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("token", "");
        if (token.isEmpty()) {
            token = ApiConfig.newToken();
            getConfig().set("token", token);
            saveConfig();
        }
        java.nio.file.Path tokenFile = getDataFolder().toPath().resolve("config.yml");
        try {
            if (java.nio.file.Files.getFileStore(tokenFile).supportsFileAttributeView("posix"))
                java.nio.file.Files.setPosixFilePermissions(tokenFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot secure plugin config", error); }
        com.commandapi.security.CommandPolicy policy = new com.commandapi.security.CommandPolicy(getConfig().getStringList("allowed-commands"));
        String mode = getConfig().getString("sender", "player");
        if (!mode.equals("player") && !mode.equals("console")) {
            throw new IllegalArgumentException("sender must be player or console");
        }
        String id = getConfig().getString("player-uuid", "");
        UUID playerId = id.isEmpty() ? null : UUID.fromString(id);
        ClientThreadBridge bridge = new ClientThreadBridge() {
            private CommandSender sender() {
                return mode.equals("console") ? Bukkit.getConsoleSender()
                        : playerId == null ? null : Bukkit.getPlayer(playerId);
            }
            @Override protected Executor clientExecutor() {
                return task -> Bukkit.getScheduler().runTask(CommandApiPlugin.this, task);
            }
            @Override protected boolean isClientThread() { return Bukkit.isPrimaryThread(); }
            @Override public boolean isInWorld() {
                return onClientThread(() -> sender() != null, false, error -> false);
            }
            @Override public String executionMode() { return "server-native-" + mode; }
            @Override public Object sessionIdentity() {
                return onClientThread(() -> sender(), null, error -> null);
            }
            @Override public String getPlayerName() {
                return onClientThread(() -> sender() == null ? null : sender().getName(), null, error -> null);
            }
            @Override protected ChatResult sendChatOnClientThread(String text) {
                CommandSender sender = sender();
                if (sender == null) return ChatResult.failure("Configured player is offline or missing");
                if (!text.startsWith("/")) return ChatResult.failure("Server adapter accepts slash commands only");
                if (!policy.allows(text)) return ChatResult.failure("Command denied by server policy");
                if (sender instanceof org.bukkit.entity.Player && !getConfig().getStringList("allowed-players").isEmpty()
                        && !getConfig().getStringList("allowed-players").contains(((org.bukkit.entity.Player) sender).getUniqueId().toString()))
                    return ChatResult.failure("Player denied by server policy");
                return Bukkit.dispatchCommand(sender, text.substring(1))
                        ? ChatResult.ok("Native command dispatched; command feedback determines outcome")
                        : ChatResult.failure("Command was not dispatched");
            }
        };
        ApiConfig config = new ApiConfig(getConfig().getString("host", "127.0.0.1"),
                getConfig().getInt("port", 0), token, true, false);
        http = new HttpServerManager(config, bridge, getDescription().getVersion(), Bukkit.getBukkitVersion(), getDataFolder().toPath());
        if (!http.start()) {
            getLogger().severe("Unable to start Command API");
            Bukkit.getPluginManager().disablePlugin(this);
        } else {
            com.commandapi.config.ConfigLoader.writeAddress(getDataFolder().toPath(), http.getHost(), http.getPort());
        }
    }
    @Override public void onDisable() {
        if (http != null) http.stop();
        com.commandapi.config.ConfigLoader.deleteAddress(getDataFolder().toPath());
    }
}
