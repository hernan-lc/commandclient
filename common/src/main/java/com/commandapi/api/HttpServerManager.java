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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The REST API server. Version independent: every Minecraft interaction goes
 * through {@link MinecraftBridge}.
 *
 * <pre>
 * GET  /api/status   server + player state
 * POST /api/chat     send chat message(s) as the local player
 * POST /api/execute  alias of /api/chat, kept for compatibility
 * anything else      404 with a JSON body
 * </pre>
 *
 * <p>Handlers never let an exception escape: an uncaught error would leave the
 * client hanging on a half-open connection and kill the worker thread.</p>
 */
public final class HttpServerManager {

    private static final Gson GSON = new Gson();

    private final ApiConfig config;
    private final MinecraftBridge bridge;
    private final TokenAuthenticator authenticator;
    private final String modVersion;
    private final String minecraftVersion;

    private HttpServer server;
    private ExecutorService executor;
    private Thread shutdownHook;

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
            executor = Executors.newCachedThreadPool(daemonThreadFactory());
            server.setExecutor(executor);

            server.createContext("/api/chat", new ChatHandler());
            server.createContext("/api/execute", new ChatHandler());
            server.createContext("/api/status", new StatusHandler());
            // Catch-all so unknown paths get a JSON 404 instead of an empty body.
            server.createContext("/", new NotFoundHandler());

            server.start();
            registerShutdownHook();
            warnIfInsecurelyExposed();
            System.out.println("[CommandAPI] Listening on http://" + getServerAddress());
            return true;
        } catch (IOException e) {
            System.err.println("[CommandAPI] Failed to start HTTP server on "
                    + config.getHost() + ":" + config.getPort() + " - " + e.getMessage());
            server = null;
            return false;
        }
    }

    /** Stops the server and releases the port. Safe to call more than once. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println("[CommandAPI] HTTP server stopped");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // Already shutting down; the hook is running or has run.
            }
            shutdownHook = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    /**
     * Worker threads are daemons so a lingering request can never keep the
     * Minecraft client's JVM alive after the window closes.
     */
    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "commandapi-http-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Releases the port on JVM exit. A Fabric client-stopping event would need
     * Fabric API, which this mod deliberately does not depend on.
     */
    private void registerShutdownHook() {
        shutdownHook = new Thread(() -> {
            HttpServer running = server;
            if (running != null) {
                running.stop(0);
            }
        }, "commandapi-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
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
                if (baos.size() > ApiLimits.MAX_BODY_BYTES) {
                    throw new RequestTooLargeException("Request body exceeds "
                            + ApiLimits.MAX_BODY_BYTES + " bytes");
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

    /**
     * Wraps a handler so no failure escapes: the client always gets a response
     * and the exchange is always closed.
     */
    private abstract class SafeHandler implements HttpHandler {
        @Override
        public final void handle(HttpExchange exchange) throws IOException {
            try {
                handleSafely(exchange);
            } catch (RequestTooLargeException e) {
                sendError(exchange, 413, e.getMessage());
            } catch (ApiRequestException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (IOException e) {
                // Client hung up mid-response; nothing left to send.
                System.err.println("[CommandAPI] Connection error: " + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("[CommandAPI] Unhandled error: " + e);
                try {
                    sendError(exchange, 500, "Internal error");
                } catch (IOException ignored) {
                    // Response already started or client gone.
                }
            } finally {
                exchange.close();
            }
        }

        abstract void handleSafely(HttpExchange exchange) throws IOException;
    }

    private final class ChatHandler extends SafeHandler {
        @Override
        void handleSafely(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                sendError(exchange, 405, "Method not allowed - Use POST");
                return;
            }

            ApiRequest request = ApiRequest.parse(readBody(exchange));

            // Checked before sending so an offline client gets 503 rather than
            // a 200 whose body says every message failed.
            if (!bridge.isInWorld()) {
                sendJson(exchange, 503, ApiResponse.unavailable(
                        "Player not available (not in world?)", request.getMessages(), request.isBatch()));
                return;
            }

            List<JsonObject> results = new ArrayList<>();
            for (String message : request.getMessages()) {
                ChatResult result = bridge.sendChat(message);
                results.add(ApiResponse.chatResult(message, result));
            }
            sendJson(exchange, 200, ApiResponse.chat(results, request.isBatch()));
        }
    }

    private final class StatusHandler extends SafeHandler {
        @Override
        void handleSafely(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) {
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendError(exchange, 405, "Method not allowed - Use GET");
                return;
            }
            boolean inWorld = bridge.isInWorld();
            sendJson(exchange, 200, ApiResponse.status(config, getHost(), getPort(),
                    inWorld, inWorld ? bridge.getPlayerName() : null,
                    modVersion, minecraftVersion));
        }
    }

    private final class NotFoundHandler extends SafeHandler {
        @Override
        void handleSafely(HttpExchange exchange) throws IOException {
            sendError(exchange, 404, "Unknown endpoint: " + exchange.getRequestURI().getPath()
                    + " - try /api/status, /api/chat or /api/execute");
        }
    }
}
