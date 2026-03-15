package com.commandapi;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Main mod class for Command API.
 * Provides a REST API to execute Minecraft commands remotely.
 * 
 * Compatible with:
 * - Single Player (Integrated Server)
 * - Dedicated Server
 */
public class CommandApiMod implements ModInitializer {
    public static final String MOD_ID = "command-api";
    
    private ApiConfig config;
    private HttpServerManager httpServerManager;
    private MinecraftServer minecraftServer;
    
    @Override
    public void onInitialize() {
        // Load configuration
        config = new ApiConfig();
        config.logConfig();
        
        // Initialize HTTP server manager
        httpServerManager = new HttpServerManager(config);
        
        // Register server lifecycle callbacks to get the server instance
        // This works for both single player and dedicated server
        registerServerCallbacks();
        
        System.out.println("Command API Mod initialized - " + MOD_ID);
    }
    
    private void registerServerCallbacks() {
        // Listen for server starting to get the MinecraftServer instance
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            System.out.println("[CommandAPI] Server starting, preparing HTTP server...");
            this.minecraftServer = server;
            httpServerManager.setMinecraftServer(server);
        });
        
        // Start HTTP server when server is ready
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("[CommandAPI] Server started, starting HTTP server...");
            httpServerManager.start();
        });
        
        // Stop HTTP server when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            System.out.println("[CommandAPI] Server stopping, stopping HTTP server...");
            httpServerManager.stop();
        });
    }
    
    /**
     * Get the current MinecraftServer instance.
     * Can be used by other mods or for debugging.
     */
    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }
    
    /**
     * Get the HTTP server manager.
     * Can be used to access API endpoints.
     */
    public HttpServerManager getHttpServerManager() {
        return httpServerManager;
    }
    
    /**
     * Get the mod configuration.
     */
    public ApiConfig getConfig() {
        return config;
    }
}
