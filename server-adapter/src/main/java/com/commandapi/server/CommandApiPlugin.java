package com.commandapi.server;
import com.commandapi.api.HttpServerManager;
import com.commandapi.config.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
/** Owns transports and lifecycle; dispatcher contains no HTTP implementation. */
public final class CommandApiPlugin extends JavaPlugin implements org.bukkit.event.Listener {
    private HttpServerManager http;
    private BukkitCommandExecutor executor;
    private BukkitTask tick;
    private PluginMessageBridge messages;
    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            if (getConfig().getString("token", "").isEmpty()) { getConfig().set("token", ApiConfig.newToken()); saveConfig(); }
            java.nio.file.Path configPath = getDataFolder().toPath().resolve("config.yml");
            if (java.nio.file.Files.getFileStore(configPath).supportsFileAttributeView("posix"))
                java.nio.file.Files.setPosixFilePermissions(configPath, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            ServerSettings settings = new ServerSettings(getConfig());
            executor = new BukkitCommandExecutor(this, settings);
            messages = new PluginMessageBridge(this, executor);
            getServer().getMessenger().registerIncomingPluginChannel(this, com.commandapi.protocol.BridgeProtocol.CHANNEL, messages);
            getServer().getMessenger().registerOutgoingPluginChannel(this, com.commandapi.protocol.BridgeProtocol.CHANNEL);
            getServer().getPluginManager().registerEvents(this, this);
            tick = Bukkit.getScheduler().runTaskTimer(this, executor::tick, 1, 1);
            if (settings.httpEnabled) {
                http = new HttpServerManager(settings.api, executor, getDescription().getVersion(), Bukkit.getBukkitVersion(), getDataFolder().toPath());
                if (!http.start()) throw new IllegalStateException("HTTP endpoint could not start");
                if (!ConfigLoader.writeAddress(getDataFolder().toPath(), http.getHost(), http.getPort())) throw new IllegalStateException("Address discovery file could not be written");
            }
        } catch (Exception error) {
            getLogger().severe("CommandAPI startup rejected: " + (error instanceof IllegalArgumentException ? error.getMessage() : error.getClass().getSimpleName()));
            onDisable(); Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    @org.bukkit.event.EventHandler
    public void disconnected(org.bukkit.event.player.PlayerQuitEvent event) { if (messages != null) messages.disconnected(event.getPlayer()); }
    @Override public void onDisable() {
        if (messages != null) { messages.close(); messages = null; }
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        org.bukkit.event.HandlerList.unregisterAll((org.bukkit.event.Listener) this);
        if (http != null) { http.stop(); http = null; }
        if (tick != null) { tick.cancel(); tick = null; }
        if (executor != null) { executor.close(); executor = null; }
        ConfigLoader.deleteAddress(getDataFolder().toPath());
    }
}
