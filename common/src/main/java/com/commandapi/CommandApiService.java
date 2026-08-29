package com.commandapi;

import com.commandapi.api.HttpServerManager;
import com.commandapi.config.ApiConfig;
import com.commandapi.config.ConfigLoader;
import com.commandapi.minecraft.MinecraftBridge;

import java.nio.file.Path;

/**
 * Version independent startup logic: load the config, start the HTTP server,
 * print the banner. Version modules only supply a config directory and a
 * {@link MinecraftBridge}.
 */
public final class CommandApiService {

    public static final String MOD_ID = "commandapi";

    private final ApiConfig config;
    private final HttpServerManager httpServerManager;

    public CommandApiService(Path configDir, MinecraftBridge bridge,
                             String modVersion, String minecraftVersion) {
        this.config = ConfigLoader.load(configDir);
        this.httpServerManager = new HttpServerManager(config, bridge, modVersion, minecraftVersion);
    }

    /** Starts the server and logs the endpoint banner. */
    public void start() {
        System.out.println("[CommandAPI] " + config);
        if (httpServerManager.start()) {
            logStartupInfo();
        }
    }

    public void stop() {
        httpServerManager.stop();
    }

    private void logStartupInfo() {
        System.out.println("=================================");
        System.out.println("[CommandAPI] Client API running at http://" + getServerAddress());
        System.out.println("  - GET  /api/status  (API and player state)");
        System.out.println("  - POST /api/chat    (Send chat message)");
        System.out.println("  - POST /api/execute (Alias for /api/chat)");
        System.out.println("=================================");
    }

    public ApiConfig getConfig() {
        return config;
    }

    public HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }

    public String getServerAddress() {
        return httpServerManager.getServerAddress();
    }

    public boolean isRunning() {
        return httpServerManager.isRunning();
    }
}
