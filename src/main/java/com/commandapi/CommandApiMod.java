package com.commandapi;

import net.fabricmc.api.ClientModInitializer;

/**
 * Main mod class for Command API (Chat version).
 * Provides a REST API to send chat messages from the client.
 * 
 * HTTP Endpoints:
 * - GET /api/status - Check if API is running
 * - POST /api/chat - Send chat message
 * 
 * Configuration:
 * Create config/commandapi.json with:
 * {
 * "port": 8080,
 * "host": "0.0.0.0",
 * "token": "your-secret-token",
 * "authEnabled": false
 * }
 * 
 * Or use system properties:
 * -Dapi.port=8080
 * -Dapi.host=0.0.0.0
 * -Dapi.token=secret
 * -Dapi.auth.enabled=true
 */
public class CommandApiMod implements ClientModInitializer {
    public static final String MOD_ID = "command-api";

    private ApiConfig config;
    private HttpServerManager httpServerManager;

    @Override
    public void onInitializeClient() {
        // Load configuration
        config = new ApiConfig();
        config.logConfig();

        // Initialize HTTP server manager
        httpServerManager = new HttpServerManager(config);

        // Start HTTP server
        httpServerManager.start();

        // Log startup info
        logStartupInfo();

        System.out.println("[ChatAPI] Client-side API initialized - " + MOD_ID);
    }

    private void logStartupInfo() {
        String serverAddress = httpServerManager.getServerAddress();
        System.out.println("=================================");
        System.out.println("[CommandAPI] Server is running!");
        System.out.println("[CommandAPI] Access API at: http://" + serverAddress);
        System.out.println("[CommandAPI] Endpoints:");
        System.out.println("  - GET  /api/status  (Check if running)");
        System.out.println("  - POST /api/chat    (Send chat message)");
        System.out.println("=================================");
    }

    public HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }

    public ApiConfig getConfig() {
        return config;
    }

    /**
     * Get the server address for external use
     */
    public String getServerAddress() {
        if (httpServerManager != null && httpServerManager.isRunning()) {
            return httpServerManager.getServerAddress();
        }
        return config.getHost() + ":" + config.getPort();
    }

    /**
     * Check if the API server is running
     */
    public boolean isRunning() {
        return httpServerManager != null && httpServerManager.isRunning();
    }
}
