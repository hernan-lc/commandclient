package com.commandapi;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Manages the HTTP server for sending chat messages.
 */
public class HttpServerManager {
    private static final Gson GSON = new Gson();
    
    private final ApiConfig config;
    private HttpServer server;
    
    public HttpServerManager(ApiConfig config) {
        this.config = config;
    }
    
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            
            server.createContext("/api/chat", new ChatHandler());
            // Keep execute for backward compatibility but map it to chat
            server.createContext("/api/execute", new ChatHandler());
            server.createContext("/api/status", new StatusHandler());
            
            server.start();
            System.out.println("Chat API Server started on port " + config.getPort());
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }
    }
    
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
    
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

    private JsonObject sendChatMessage(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("text", message);
        
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Sending chat via the player object
                mc.player.chat(message);
                result.addProperty("success", true);
                result.addProperty("output", "Message sent to chat");
            } else {
                result.addProperty("success", false);
                result.addProperty("output", "Player not available (not in world?)");
            }
        } catch (Exception e) {
            result.addProperty("success", false);
            result.addProperty("output", "Error: " + e.getMessage());
        }
        
        return result;
    }
    
    class ChatHandler implements HttpHandler {
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
            
            try {
                String requestBody;
                try (java.io.InputStream is = exchange.getRequestBody()) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, len);
                    }
                    requestBody = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                }
                JsonObject request = GSON.fromJson(requestBody, JsonObject.class);
                JsonObject response = new JsonObject();
                
                if (request.has("text") || request.has("command")) {
                    String msg = request.has("text") ? request.get("text").getAsString() : request.get("command").getAsString();
                    response.add("result", sendChatMessage(msg));
                    response.addProperty("success", true);
                } else if (request.has("messages")) {
                    JsonArray messages = request.getAsJsonArray("messages");
                    JsonArray results = new JsonArray();
                    for (int i = 0; i < messages.size(); i++) {
                        results.add(sendChatMessage(messages.get(i).getAsString()));
                    }
                    response.add("results", results);
                    response.addProperty("success", true);
                } else {
                    sendError(exchange, 400, "Missing 'text' or 'messages' field");
                    return;
                }
                
                sendJson(exchange, 200, response);
            } catch (Exception e) {
                sendError(exchange, 500, "Internal error: " + e.getMessage());
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
            status.addProperty("mode", "client-chat");
            
            Minecraft mc = Minecraft.getInstance();
            status.addProperty("in_world", mc.player != null);
            if (mc.player != null) {
                status.addProperty("player_name", mc.player.getName().getString());
            }
            
            sendJson(exchange, 200, status);
        }
    }
}
