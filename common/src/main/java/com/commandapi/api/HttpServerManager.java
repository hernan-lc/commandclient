package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The REST API server. Version independent: every Minecraft interaction goes
 * through {@link MinecraftBridge}.
 *
 * <pre>
 * GET  /api/status   server + player state
 * POST /api/chat     send chat message(s) as the local player
 * POST /api/execute  alias of /api/chat, kept for compatibility
 * </pre>
 */
public final class HttpServerManager {

    private static final Gson GSON = new Gson();
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private final ApiConfig config;
    private final MinecraftBridge bridge;
    private final TokenAuthenticator authenticator;
    private final String modVersion;
    private final String minecraftVersion;

    private HttpServer server;
    private ExecutorService executor;

    public HttpServerManager(ApiConfig config, MinecraftBridge bridge,
                             String modVersion, String minecraftVersion) {
        this.config = config;
        this.bridge = bridge;
        this.authenticator = new TokenAuthenticator(config);
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
    }

    /** Binds and starts the server. Returns false if the port could not be bound. */
    public boolean start() {
        try {
            server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);

            server.createContext("/api/chat", new ChatHandler());
            server.createContext("/api/execute", new ChatHandler());
            server.createContext("/api/status", new StatusHandler());

            server.start();
            warnIfInsecurelyExposed();
            System.out.println("[CommandAPI] Listening on http://" + getServerAddress());
            return true;
        } catch (IOException e) {
            System.err.println("[CommandAPI] Failed to start HTTP server: " + e.getMessage());
            server = null;
            return false;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    /** {@code host:port}, resolved from the bound socket once running. */
    public String getServerAddress() {
        return getHost() + ":" + getPort();
    }

    public String getHost() {
        if (server != null && server.getAddress() != null) {
            return server.getAddress().getAddress().getHostAddress();
        }
        return config.getHost();
    }

    public int getPort() {
        if (server != null && server.getAddress() != null) {
            return server.getAddress().getPort();
        }
        return config.getPort();
    }

    private void warnIfInsecurelyExposed() {
        if (config.isExposedBeyondLoopback() && !config.isAuthEnabled()) {
            System.err.println("[CommandAPI] WARNING: bound to " + config.getHost()
                    + " with authentication DISABLED. Anyone who can reach this port can"
                    + " send chat and commands as you. Set \"authEnabled\": true and a"
                    + " token, or bind to 127.0.0.1.");
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
                if (baos.size() > MAX_BODY_BYTES) {
                    throw new ApiRequestException("Request body too large");
                }
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, Object response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        sendJson(exchange, statusCode, ApiResponse.error(statusCode, message));
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        if (authenticator.isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
            return true;
        }
        sendError(exchange, 401, "Unauthorized");
        return false;
    }

    private final class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!authorize(exchange)) {
                    return;
                }
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendError(exchange, 405, "Method not allowed - Use POST");
                    return;
                }

                ApiRequest request = ApiRequest.parse(readBody(exchange));
                List<JsonObject> results = new ArrayList<>();
                for (String message : request.getMessages()) {
                    ChatResult result = bridge.sendChat(message);
                    results.add(ApiResponse.chatResult(message, result));
                }
                sendJson(exchange, 200, ApiResponse.chat(results, request.isBatch()));
            } catch (ApiRequestException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (RuntimeException e) {
                sendError(exchange, 500, "Internal error: " + e);
            } finally {
                exchange.close();
            }
        }
    }

    private final class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!authorize(exchange)) {
                    return;
                }
                boolean inWorld = bridge.isInWorld();
                sendJson(exchange, 200, ApiResponse.status(config, getHost(), getPort(),
                        inWorld, inWorld ? bridge.getPlayerName() : null,
                        modVersion, minecraftVersion));
            } catch (RuntimeException e) {
                sendError(exchange, 500, "Internal error: " + e);
            } finally {
                exchange.close();
            }
        }
    }
}
