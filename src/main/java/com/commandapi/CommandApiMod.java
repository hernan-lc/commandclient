package com.commandapi;

import net.fabricmc.api.ClientModInitializer;

/**
 * Main mod class for Command API (Chat version).
 * Provides a REST API to send chat messages from the client.
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
        
        // Start HTTP server immediately
        httpServerManager.start();
        
        System.out.println("[ChatAPI] Client-side API initialized - " + MOD_ID);
    }
    
    public HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }
    
    public ApiConfig getConfig() {
        return config;
    }
}
