package com.commandapi;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Manages the HTTP server for the Command API.
 */
public class HttpServerManager {
    private static final Gson GSON = new Gson();
    
    private final ApiConfig config;
    private HttpServer server;
    private MinecraftServer minecraftServer;
    
    public HttpServerManager(ApiConfig config) {
        this.config = config;
    }
    
    public void setMinecraftServer(MinecraftServer server) {
        this.minecraftServer = server;
    }
    
    public MinecraftServer getMinecraftServer() {
        return minecraftServer;
    }
    
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            
            server.createContext("/api/execute", new ExecuteCommandHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/stop", new StopHandler());
            
            server.start();
            System.out.println("HTTP Server started on port " + config.getPort());
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
    // ==================== Authentication ====================
    
    boolean authenticate(HttpExchange exchange) {
        if (!config.isAuthEnabled() || config.getToken().isEmpty()) {
            return true;
        }
        
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String receivedToken = authHeader.substring(7);
            return receivedToken.equals(config.getToken());
        }
        
        return false;
    }
    
    // ==================== Response Helpers ====================
    
    void sendJson(HttpExchange exchange, int statusCode, Object response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String json = GSON.toJson(response);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
    
    void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        sendJson(exchange, statusCode, error);
    }
    
    // ==================== Helper Methods using Reflection ====================
    
    private Object getCommandManager() {
        try {
            // Try to get the commandManager field via reflection
            Field field = MinecraftServer.class.getDeclaredField("commandManager");
            field.setAccessible(true);
            return field.get(minecraftServer);
        } catch (Exception e) {
            // Fallback: try the getter method
            try {
                Method method = MinecraftServer.class.getMethod("getCommandManager");
                return method.invoke(minecraftServer);
            } catch (Exception ex) {
                System.err.println("Could not get command manager: " + ex.getMessage());
                return null;
            }
        }
    }
    
    private Object getCommandSource() {
        try {
            // Try to get the serverCommandSource field via reflection
            Field field = MinecraftServer.class.getDeclaredField("serverCommandSource");
            field.setAccessible(true);
            return field.get(minecraftServer);
        } catch (Exception e) {
            // Fallback: try the getter method
            try {
                Method method = MinecraftServer.class.getMethod("getCommandSource");
                return method.invoke(minecraftServer);
            } catch (Exception ex) {
                System.err.println("Could not get command source: " + ex.getMessage());
                return null;
            }
        }
    }
    
    private int getPlayerCount() {
        try {
            // Try getPlayerList().size() first (older mappings)
            Method getPlayerList = MinecraftServer.class.getMethod("getPlayerList");
            Object playerList = getPlayerList.invoke(minecraftServer);
            
            // Try size() method
            try {
                Method size = playerList.getClass().getMethod("size");
                return (int) size.invoke(playerList);
            } catch (NoSuchMethodException e) {
                // Try getCurrentPlayerCount() (newer mappings)
                try {
                    Method getCurrentPlayerCount = playerList.getClass().getMethod("getCurrentPlayerCount");
                    return (int) getCurrentPlayerCount.invoke(playerList);
                } catch (NoSuchMethodException ex) {
                    return 0;
                }
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    private int getMaxPlayers() {
        try {
            Method getPlayerList = MinecraftServer.class.getMethod("getPlayerList");
            Object playerList = getPlayerList.invoke(minecraftServer);
            
            try {
                Method getMaxPlayers = playerList.getClass().getMethod("getMaxPlayers");
                return (int) getMaxPlayers.invoke(playerList);
            } catch (NoSuchMethodException e) {
                // Try getMaxPlayerCount()
                try {
                    Method getMaxPlayerCount = playerList.getClass().getMethod("getMaxPlayerCount");
                    return (int) getMaxPlayerCount.invoke(playerList);
                } catch (NoSuchMethodException ex) {
                    return 0;
                }
            }
        } catch (Exception e) {
            return 0;
        }
    }
    
    // ==================== Command Execution ====================
    
    private JsonObject executeCommand(String command) {
        JsonObject result = new JsonObject();
        result.addProperty("command", command);
        
        try {
            if (minecraftServer != null) {
                Object commandManager = getCommandManager();
                Object commandSource = getCommandSource();
                
                if (commandManager != null && commandSource != null) {
                    // Use reflection to call execute method
                    Method execute = commandManager.getClass().getMethod("execute", Object.class, String.class);
                    int success = (int) execute.invoke(commandManager, commandSource, command);
                    
                    result.addProperty("success", success > 0);
                    result.addProperty("output", success > 0 ? "Command executed successfully" : "Command failed");
                    System.out.println("Executed command: " + command + " - Success: " + (success > 0));
                } else {
                    result.addProperty("success", false);
                    result.addProperty("output", "Command manager not available");
                }
            } else {
                result.addProperty("success", false);
                result.addProperty("output", "Server not available");
            }
        } catch (Exception e) {
            result.addProperty("success", false);
            result.addProperty("output", "Error: " + e.getMessage());
            System.err.println("Error executing command: " + command + " - " + e.getMessage());
        }
        
        return result;
    }
    
    // ==================== HTTP Handlers ====================
    
    class ExecuteCommandHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authenticate(exchange)) {
                sendError(exchange, 401, "Unauthorized - Invalid or missing token");
                return;
            }
            
            if (minecraftServer == null) {
                sendError(exchange, 503, "Server not running");
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed - Use POST");
                return;
            }
            
            try {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject request = GSON.fromJson(requestBody, JsonObject.class);
                
                JsonObject response = new JsonObject();
                
                if (request.has("commands")) {
                    // Multiple commands
                    JsonArray commands = request.getAsJsonArray("commands");
                    JsonArray results = new JsonArray();
                    
                    for (int i = 0; i < commands.size(); i++) {
                        String command = commands.get(i).getAsString();
                        results.add(executeCommand(command));
                    }
                    
                    response.add("results", results);
                } else if (request.has("command")) {
                    // Single command
                    String command = request.get("command").getAsString();
                    response.add("result", executeCommand(command));
                } else {
                    sendError(exchange, 400, "Missing 'command' or 'commands' field");
                    return;
                }
                
                response.addProperty("success", true);
                sendJson(exchange, 200, response);
                
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }
    
    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authenticate(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            
            JsonObject status = new JsonObject();
            status.addProperty("status", "running");
            status.addProperty("mod", "Command API");
            status.addProperty("version", "1.0.0");
            status.addProperty("server_loaded", minecraftServer != null);
            
            if (minecraftServer != null) {
                status.addProperty("player_count", getPlayerCount());
                status.addProperty("max_players", getMaxPlayers());
                status.addProperty("server_name", "Fabric Server");
            }
            
            sendJson(exchange, 200, status);
        }
    }
    
    class StopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authenticate(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed - Use POST");
                return;
            }
            
            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message", "Server shutdown initiated");
            sendJson(exchange, 200, response);
            
            // Schedule server shutdown
            if (minecraftServer != null) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        // Try to shut down the server
                        System.exit(0);
                    } catch (Exception e) {
                        System.err.println("Error shutting down server: " + e.getMessage());
                    }
                }).start();
            }
        }
    }
}
