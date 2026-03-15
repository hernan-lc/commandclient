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
    private static MinecraftServer minecraftServer;
    
    @Override
    public void onInitialize() {
        // Load configuration
        config = new ApiConfig();
        config.logConfig();
        
        // Initialize HTTP server manager
        httpServerManager = new HttpServerManager(config);
        
        // Register server lifecycle callbacks
        registerServerCallbacks();
        
        System.out.println("Command API Mod initialized - " + MOD_ID);
    }
    
    private void registerServerCallbacks() {
        // For dedicated server and single player
        // Use SERVER_STARTING event
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            System.out.println("[CommandAPI] Server starting, setting up...");
            minecraftServer = server;
            httpServerManager.setMinecraftServer(server);
        });
        
        // When server is fully started, start HTTP server
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("[CommandAPI] Server fully started!");
            minecraftServer = server;
            httpServerManager.setMinecraftServer(server);
            httpServerManager.start();
        });
        
        // When server is stopping
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            System.out.println("[CommandAPI] Server stopping...");
            httpServerManager.stop();
        });
    }
    
    // Static method to get server (for fallback)
    public static MinecraftServer getServer() {
        return minecraftServer;
    }
    
    /**
     * Get the current MinecraftServer instance.
     */
    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }
    
    /**
     * Get the HTTP server manager.
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
