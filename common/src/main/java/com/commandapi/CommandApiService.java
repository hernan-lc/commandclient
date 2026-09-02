package com.commandapi;

import com.commandapi.api.HttpServerManager;
import com.commandapi.config.ApiConfig;
import com.commandapi.config.ConfigCommandHandler;
import com.commandapi.config.ConfigLoader;
import com.commandapi.minecraft.MinecraftBridge;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Version independent startup logic: load the config, start the HTTP server,
 * print the banner. Version modules only supply a config directory and a
 * {@link MinecraftBridge}.
 *
 * <p>The service also backs the in-game {@code /commandapi} commands: it can
 * restart the server on a changed config and persists changes to
 * {@code commandapi.json} so they survive restarts.</p>
 */
public final class CommandApiService implements ConfigCommandHandler.Actions {

    public static final String MOD_ID = "commandapi";

    private final Path configDir;
    private final MinecraftBridge bridge;
    private final String modVersion;
    private final String minecraftVersion;
    private final ConfigCommandHandler commandHandler;

    private ApiConfig config;
    private HttpServerManager httpServerManager;

    public CommandApiService(Path configDir, MinecraftBridge bridge,
                             String modVersion, String minecraftVersion) {
        this.configDir = configDir;
        this.bridge = bridge;
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
        this.commandHandler = new ConfigCommandHandler(this);
        this.config = ConfigLoader.load(configDir);
    }

    /** Starts the server and logs the endpoint banner. */
    public synchronized void start() {
        config = ConfigLoader.load(configDir);
        if (startServer()) {
            logStartupInfo();
        } else {
            logBindFailure();
        }
    }

    public synchronized void stop() {
        stopServer();
        ConfigLoader.deleteAddress(configDir);
    }

    /**
     * Runs one {@code /commandapi} line (the part after the command name) and
     * returns one chat line per entry. Never throws.
     */
    public List<String> runCommand(String args) {
        try {
            return commandHandler.handle(args);
        } catch (RuntimeException e) {
            return Collections.singletonList("[CommandAPI] Error: " + e.getMessage());
        }
    }

    // ConfigCommandHandler.Actions

    @Override
    public synchronized ApiConfig currentConfig() {
        return config;
    }

    @Override
    public synchronized boolean isRunning() {
        return httpServerManager != null && httpServerManager.isRunning();
    }

    @Override
    public synchronized String runningAddress() {
        if (httpServerManager != null) {
            return httpServerManager.getServerAddress();
        }
        return config.getHost() + ":" + config.getPort();
    }

    @Override
    public synchronized boolean applyAndRestart(ApiConfig newConfig) {
        if (newConfig == null) {
            return false;
        }
        stopServer();
        config = newConfig;
        ConfigLoader.save(configDir, newConfig);
        return startServer();
    }

    @Override
    public synchronized boolean reloadFromDisk() {
        stopServer();
        config = ConfigLoader.load(configDir);
        return startServer();
    }

    public synchronized ApiConfig getConfig() {
        return config;
    }

    /** The live server manager, or null when the server is not running. */
    public synchronized HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }

    public synchronized String getServerAddress() {
        if (httpServerManager != null) {
            return httpServerManager.getServerAddress();
        }
        return config.getHost() + ":" + config.getPort();
    }

    private boolean startServer() {
        httpServerManager = new HttpServerManager(config, bridge, modVersion, minecraftVersion);
        if (!httpServerManager.start()) {
            httpServerManager = null;
            return false;
        }
        ConfigLoader.writeAddress(configDir,
                httpServerManager.getHost(), httpServerManager.getPort());
        return true;
    }

    private void stopServer() {
        if (httpServerManager != null) {
            httpServerManager.stop();
            httpServerManager = null;
        }
    }

    private void logStartupInfo() {
        System.out.println("[CommandAPI] " + config);
        System.out.println("=================================");
        System.out.println("[CommandAPI] Client API running at http://" + getServerAddress());
        System.out.println("  - GET  /api/status  (API and player state)");
        System.out.println("  - POST /api/chat    (Send chat message)");
        System.out.println("  - POST /api/execute (Alias for /api/chat)");
        System.out.println("  - In game: /commandapi help (change config from chat)");
        System.out.println("=================================");
    }

    private void logBindFailure() {
        System.out.println("[CommandAPI] " + config);
        System.err.println("[CommandAPI] Could not bind " + config.getHost() + ":" + config.getPort()
                + " - is another client (or a stale process) already using it?");
        System.err.println("[CommandAPI] Fix: set \"port\": 0 in "
                + configFileHint() + " for an automatic port,");
        System.err.println("[CommandAPI] or pick a free port with /commandapi port 0 once in game.");
    }

    private String configFileHint() {
        if (configDir != null) {
            return configDir.resolve(ConfigLoader.CONFIG_FILE_NAME).toString();
        }
        return ConfigLoader.CONFIG_FILE_NAME;
    }
}
