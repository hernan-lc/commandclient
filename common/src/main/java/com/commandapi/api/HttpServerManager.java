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
import java.nio.file.Path;
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
    private final Path configDir;

    private JobManager jobs;
    private com.commandapi.functions.FunctionRegistry functionRegistry;
    private com.commandapi.functions.FunctionLimits functionLimits;
    private HttpServer server;
    private ExecutorService executor;
    private Thread shutdownHook;

    public HttpServerManager(ApiConfig config, MinecraftBridge bridge,
                             String modVersion, String minecraftVersion) {
        this(config, bridge, modVersion, minecraftVersion, null);
    }

    public HttpServerManager(ApiConfig config, MinecraftBridge bridge,
                             String modVersion, String minecraftVersion, Path configDir) {
        this.config = config;
        this.bridge = bridge;
        bridge.configure(config);
        this.authenticator = new TokenAuthenticator(config);
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
        this.configDir = configDir;
    }

    /** Binds and starts the server. Returns false if the port could not be bound. */
    public boolean start() {
        try {
            if (config.isExposedBeyondLoopback() && (!config.isAllowRemotePrivileged() || !config.isAuthEnabled())) {
                System.err.println("[CommandAPI] Remote binding requires authentication and allowRemotePrivileged=true");
                return false;
            }
            server = HttpServer.create(new InetSocketAddress(config.getHost(), config.getPort()), 0);
            executor = new java.util.concurrent.ThreadPoolExecutor(config.getHttpWorkers(), config.getHttpWorkers(), 0, java.util.concurrent.TimeUnit.SECONDS,
                    new java.util.concurrent.ArrayBlockingQueue<>(config.getMaxHttpQueue()), daemonThreadFactory(),
                    new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(executor);

            server.createContext("/api/chat", new ChatHandler());
            server.createContext("/api/execute", new ChatHandler());
            server.createContext("/api/status", new StatusHandler());
            // Catch-all so unknown paths get a JSON 404 instead of an empty body.
            server.createContext("/", new NotFoundHandler());

            jobs = new JobManager(bridge, config);
            functionRegistry = new com.commandapi.functions.FunctionRegistry();
            functionLimits = com.commandapi.functions.FunctionLimits.defaults();
            jobs.setFunctions(functionRegistry, functionLimits, minecraftVersion);
            loadFunctions();
            server.createContext("/api/jobs", new JobsHandler());
            server.createContext("/api/functions", new FunctionsHandler());
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
        if (jobs != null) { jobs.close(); jobs = null; }
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
            stop();
            com.commandapi.config.ConfigLoader.deleteAddress(configDir);
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
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(baos.toByteArray())).toString();
            } catch (java.nio.charset.CharacterCodingException error) { throw new ApiRequestException("Request body is not valid UTF-8"); }
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
        if (authenticator.isAuthorized(exchange.getRequestHeaders().getFirst("Authorization"),
                !exchange.getRequestMethod().equals("GET"))) {
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
                String path = exchange.getRequestURI().getRawPath();
                if (exchange.getRequestHeaders().containsKey("Origin")
                        || "cross-site".equals(exchange.getRequestHeaders().getFirst("Sec-Fetch-Site"))) {
                    sendError(exchange, 403, "Browser-origin requests are not supported"); return;
                }
                if (!(path.equals("/api/chat") || path.equals("/api/execute") || path.equals("/api/status")
                        || path.equals("/api/jobs") || path.matches("/api/jobs/[a-f0-9-]{36}")
                        || isFunctionPath(path))) {
                    sendError(exchange, 404, "Unknown endpoint"); return;
                }
                if (exchange.getRequestMethod().equals("POST")) {
                    String type = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (type == null || !type.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                        sendError(exchange, 415, "Content-Type must be application/json"); return;
                    }
                }
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

    private boolean authorizeCommands(HttpExchange exchange, ApiRequest request) throws IOException {
        for (String text : request.getMessages()) {
            if (!config.getCommandPolicy().allows(text)) {
                sendError(exchange, 403, "Command denied by configured policy"); return false;
            }
        }
        return true;
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
            if (!authorizeCommands(exchange, request)) return;

            // Checked before sending so an offline client gets 503 rather than
            // a 200 whose body says every message failed.
            if (!bridge.isInWorld()) {
                sendJson(exchange, 503, ApiResponse.unavailable(
                        "Player not available (not in world?)", request.getMessages(), request.isBatch()));
                return;
            }

            CommandScheduler.Work work = jobs.execute(request);
            if (work == null) { sendError(exchange, 429, "Execution queue full"); return; }
            try { work.await(config.getExecutionTimeoutSeconds()); }
            catch (java.util.concurrent.TimeoutException error) { work.cancel("HTTP_WAIT_TIMEOUT"); }
            catch (InterruptedException error) { work.cancel("HTTP_WAIT_CANCELLED"); Thread.currentThread().interrupt(); }
            catch (java.util.concurrent.ExecutionException error) { work.cancel("EXECUTION_FAILED"); }
            List<JsonObject> results = work.results();
            for (int i = results.size(); i < request.getMessages().size(); i++)
                results.add(ApiResponse.chatResult(request.getMessages().get(i),
                        ChatResult.unknown("Completion unavailable; inspect job and do not retry automatically", "OUTCOME_UNKNOWN")));
            exchange.getResponseHeaders().set("Location", "/api/jobs/" + work.id());
            sendJson(exchange, 200, ApiResponse.chat(results, request.isBatch()));
        }
    }

    private final class JobsHandler extends SafeHandler {
        @Override void handleSafely(HttpExchange exchange) throws IOException {
            if (!authorize(exchange)) return;
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/api/jobs") && method.equals("POST")) {
                ApiRequest request = ApiRequest.parse(readBody(exchange));
            if (!authorizeCommands(exchange, request)) return;
                if (!bridge.isInWorld()) { sendError(exchange, 503, "Session unavailable"); return; }
                JsonObject job = jobs.submit(request);
                if (job == null) { sendError(exchange, 429, "Job capacity reached"); return; }
                exchange.getResponseHeaders().set("Location", "/api/jobs/" + job.get("id").getAsString());
                sendJson(exchange, 202, job);
                return;
            }
            if (path.matches("/api/jobs/[a-f0-9-]{36}") && (method.equals("GET") || method.equals("DELETE"))) {
                JsonObject job = jobs.get(path.substring("/api/jobs/".length()), method.equals("DELETE"));
                if (job == null) sendError(exchange, 404, "Job not found");
                else sendJson(exchange, 200, job);
                return;
            }
            sendError(exchange, path.equals("/api/jobs") ? 405 : 404, "Use POST /api/jobs or GET/DELETE /api/jobs/{id}");
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
            JsonObject status = ApiResponse.status(config, getHost(), getPort(),
                    inWorld, inWorld ? bridge.getPlayerName() : null, modVersion, minecraftVersion);
            status.addProperty("mode", bridge.executionMode());
            status.addProperty("execution_mode", bridge.executionMode());
            status.add("capabilities", bridge.capabilities());
            status.addProperty("mutation_auth_required", true);
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : jobs.status().entrySet()) status.add(entry.getKey(), entry.getValue());
            com.google.gson.JsonObject functions = new com.google.gson.JsonObject();
            functions.addProperty("count", functionRegistry == null ? 0 : functionRegistry.list().size());
            functions.add("capabilities", com.commandapi.functions.VersionCommandFormatter.capabilities(
                    minecraftVersion, bridge.executionMode()));
            status.add("functions", functions);
            sendJson(exchange, 200, status);
        }
    }

    /** Strict allowlist for function routes: exact paths plus encoded ids. */
    private static boolean isFunctionPath(String path) {
        if (path.equals("/api/functions") || path.equals("/api/functions/validate")
                || path.equals("/api/functions/reload")) {
            return true;
        }
        if (path.startsWith("/api/functions/")) {
            String rest;
            try {
                rest = java.net.URLDecoder.decode(path.substring("/api/functions/".length()),
                        "UTF-8");
            } catch (Exception e) {
                return false;
            }
            if (rest.equals("validate") || rest.equals("reload")) {
                return true;
            }
            if (rest.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_/.-]+")
                    || rest.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_/.-]+/(run|preview)")) {
                return true;
            }
        }
        return false;
    }

    private void loadFunctions() {
        if (functionRegistry == null || configDir == null) {
            return;
        }
        try {
            com.commandapi.functions.FunctionStorage.loadAll(configDir, functionRegistry,
                    functionLimits);
        } catch (Exception e) {
            System.err.println("[CommandAPI] Failed to load functions: " + e.getMessage());
        }
    }

    /** Registry shared by REST and CLI paths. Never null after {@link #start()}. */
    public com.commandapi.functions.FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    private String decodeFunctionId(String encoded) throws IOException {
        String id;
        try {
            id = java.net.URLDecoder.decode(encoded, "UTF-8");
        } catch (IllegalArgumentException e) {
            throw new ApiRequestException("Invalid function id encoding");
        }
        if (!id.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_/.-]+")) {
            throw new ApiRequestException("Invalid function id: " + id);
        }
        return id;
    }

    /**
     * Function management (create/update/delete/reload) writes files on disk,
     * so it has its own explicit authorization: bearer authentication must be
     * enabled and the request must carry the token. Reads stay open when auth
     * is disabled, matching the existing GET rules.
     */
    private boolean authorizeFunctionManagement(HttpExchange exchange) throws IOException {
        if (!config.isAuthEnabled()) {
            sendError(exchange, 403, "Function management requires authentication to be enabled");
            return false;
        }
        return authorize(exchange);
    }

    private Object[] readFunctionArgs(String body) {
        // Returns {argsMapOrNull, async, positionalListOrNull} packed in an array.
        com.google.gson.JsonElement element;
        try {
            element = GSON.fromJson(body, com.google.gson.JsonElement.class);
        } catch (RuntimeException e) {
            throw new ApiRequestException("Malformed JSON: " + e.getMessage());
        }
        boolean async = false;
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<String, Object>();
        java.util.List<Object> positional = null;
        if (element == null || element.isJsonNull()) {
            return new Object[]{args, async, null};
        }
        if (!element.isJsonObject()) {
            throw new ApiRequestException("Function body must be a JSON object");
        }
        com.google.gson.JsonObject json = element.getAsJsonObject();
        if (json.has("async") && !json.get("async").isJsonNull()) {
            try {
                async = json.get("async").getAsBoolean();
            } catch (RuntimeException e) {
                throw new ApiRequestException("'async' must be a boolean");
            }
        }
        if (json.has("args") && !json.get("args").isJsonNull()) {
            com.google.gson.JsonElement raw = json.get("args");
            if (raw.isJsonObject()) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry
                        : raw.getAsJsonObject().entrySet()) {
                    args.put(entry.getKey(), gsonScalar(entry.getValue()));
                }
            } else if (raw.isJsonArray()) {
                positional = new java.util.ArrayList<Object>();
                for (com.google.gson.JsonElement item : raw.getAsJsonArray()) {
                    positional.add(gsonScalar(item));
                }
            } else {
                throw new ApiRequestException("'args' must be an object or an array");
            }
        }
        return new Object[]{args, async, positional};
    }

    private static Object gsonScalar(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonPrimitive()) {
            return element.toString();
        }
        com.google.gson.JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            String text = primitive.getAsString();
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return primitive.getAsDouble();
            }
        }
        return primitive.getAsString();
    }

    private void sendFunctionDefinition(HttpExchange exchange, int status,
            com.commandapi.functions.FunctionDefinition definition) throws IOException {
        sendJson(exchange, status, com.commandapi.functions.FunctionJson.view(definition));
    }

    private final class FunctionsHandler extends SafeHandler {
        @Override void handleSafely(HttpExchange exchange) throws IOException {
            if (jobs == null) {
                sendError(exchange, 503, "Functions unavailable");
                return;
            }
            String rawPath = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();
            String prefix = "/api/functions";
            String rest = rawPath.length() > prefix.length()
                    ? rawPath.substring(prefix.length()) : "";
            if (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            String decoded;
            try {
                decoded = rest.isEmpty() ? "" : java.net.URLDecoder.decode(rest, "UTF-8");
            } catch (IllegalArgumentException e) {
                sendError(exchange, 400, "Invalid function id encoding");
                return;
            }

            if (decoded.isEmpty()) {
                if ("GET".equalsIgnoreCase(method)) {
                    if (!authorize(exchange)) {
                        return;
                    }
                    com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                    for (com.commandapi.functions.FunctionDefinition definition
                            : functionRegistry.list()) {
                        array.add(com.commandapi.functions.FunctionJson.summary(definition));
                    }
                    com.google.gson.JsonObject response = new com.google.gson.JsonObject();
                    response.add("functions", array);
                    com.google.gson.JsonObject errors = new com.google.gson.JsonObject();
                    for (java.util.Map.Entry<String, String> entry
                            : functionRegistry.errors().entrySet()) {
                        errors.addProperty(entry.getKey(), entry.getValue());
                    }
                    response.add("errors", errors);
                    sendJson(exchange, 200, response);
                    return;
                }
                if ("POST".equalsIgnoreCase(method)) {
                    if (!authorizeFunctionManagement(exchange)) {
                        return;
                    }
                    createOrUpdate(exchange, null);
                    return;
                }
                exchange.getResponseHeaders().set("Allow", "GET, POST");
                sendError(exchange, 405, "Method not allowed - Use GET or POST");
                return;
            }
            if (decoded.equals("reload")) {
                if (!"POST".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    sendError(exchange, 405, "Method not allowed - Use POST");
                    return;
                }
                if (!authorizeFunctionManagement(exchange)) {
                    return;
                }
                try {
                    com.commandapi.functions.FunctionStorage.LoadResult result =
                            com.commandapi.functions.FunctionStorage.reload(configDir,
                                    functionRegistry, functionLimits);
                    com.google.gson.JsonObject response = new com.google.gson.JsonObject();
                    response.addProperty("reloaded", true);
                    com.google.gson.JsonArray loaded = new com.google.gson.JsonArray();
                    for (String id : result.loaded) {
                        loaded.add(id);
                    }
                    response.add("loaded", loaded);
                    com.google.gson.JsonObject failures = new com.google.gson.JsonObject();
                    for (java.util.Map.Entry<String, String> entry
                            : result.failures.entrySet()) {
                        failures.addProperty(entry.getKey(), entry.getValue());
                    }
                    response.add("failures", failures);
                    sendJson(exchange, 200, response);
                } catch (Exception e) {
                    sendError(exchange, 500, "Reload failed: " + e.getMessage());
                }
                return;
            }
            if (decoded.equals("validate")) {
                if (!"POST".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    sendError(exchange, 405, "Method not allowed - Use POST");
                    return;
                }
                if (!authorize(exchange)) {
                    return;
                }
                validateBody(exchange);
                return;
            }
            if (decoded.endsWith("/run") || decoded.endsWith("/preview")) {
                boolean preview = decoded.endsWith("/preview");
                String id = decodeFunctionId(decoded.substring(0, decoded.length()
                        - (preview ? "/preview".length() : "/run".length())));
                if (!"POST".equalsIgnoreCase(method)) {
                    exchange.getResponseHeaders().set("Allow", "POST");
                    sendError(exchange, 405, "Method not allowed - Use POST");
                    return;
                }
                if (!authorize(exchange)) {
                    return;
                }
                runOrPreview(exchange, id, preview);
                return;
            }
            String id = decodeFunctionId(decoded);
            if ("GET".equalsIgnoreCase(method)) {
                if (!authorize(exchange)) {
                    return;
                }
                com.commandapi.functions.FunctionDefinition definition =
                        functionRegistry.get(id);
                if (definition == null) {
                    sendError(exchange, 404, "Function not found: " + id);
                    return;
                }
                sendFunctionDefinition(exchange, 200, definition);
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                if (!authorizeFunctionManagement(exchange)) {
                    return;
                }
                createOrUpdate(exchange, id);
                return;
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                if (!authorizeFunctionManagement(exchange)) {
                    return;
                }
                com.commandapi.functions.FunctionDefinition existing =
                        functionRegistry.get(id);
                if (existing == null) {
                    sendError(exchange, 404, "Function not found: " + id);
                    return;
                }
                try {
                    com.commandapi.functions.FunctionStorage.delete(configDir, id);
                } catch (Exception e) {
                    sendError(exchange, 500, "Delete failed: " + e.getMessage());
                    return;
                }
                functionRegistry.remove(id);
                com.google.gson.JsonObject response = new com.google.gson.JsonObject();
                response.addProperty("deleted", true);
                response.addProperty("id", id);
                sendJson(exchange, 200, response);
                return;
            }
            exchange.getResponseHeaders().set("Allow", "GET, PUT, DELETE");
            sendError(exchange, 405, "Method not allowed - Use GET, PUT or DELETE");
        }

        private void validateBody(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            com.google.gson.JsonElement element;
            try {
                element = GSON.fromJson(body, com.google.gson.JsonElement.class);
            } catch (RuntimeException e) {
                throw new ApiRequestException("Malformed JSON: " + e.getMessage());
            }
            if (element == null || !element.isJsonObject()) {
                throw new ApiRequestException("Request body must be a JSON object");
            }
            com.google.gson.JsonObject json = element.getAsJsonObject();
            String id = json.has("id") && !json.get("id").isJsonNull()
                    ? json.get("id").getAsString() : "validate:tmp";
            String format = json.has("format") && !json.get("format").isJsonNull()
                    ? json.get("format").getAsString() : null;
            String source = json.has("source") && !json.get("source").isJsonNull()
                    ? json.get("source").getAsString() : null;
            com.google.gson.JsonObject response = new com.google.gson.JsonObject();
            try {
                com.commandapi.functions.FunctionDefinition definition;
                if (source != null) {
                    String fileName = "json".equalsIgnoreCase(format) ? "tmp.json"
                            : "mcfunction".equalsIgnoreCase(format) ? "tmp.mcfunction" : null;
                    definition = com.commandapi.functions.FunctionParser.parse(id, fileName,
                            source, "api-validate");
                } else if (json.has("definition") && json.get("definition").isJsonObject()) {
                    definition = com.commandapi.functions.JsonFunctionParser.parse(id,
                            json.get("definition").toString(), "api-validate");
                } else {
                    throw new com.commandapi.functions.FunctionException(
                            "Provide 'source' or 'definition'", "MISSING_FIELD");
                }
                com.commandapi.functions.FunctionValidator.validate(definition, functionLimits);
                response.addProperty("valid", true);
                response.addProperty("id", definition.getId());
                response.add("definition",
                        com.commandapi.functions.FunctionJson.summary(definition));
            } catch (com.commandapi.functions.FunctionException e) {
                response.addProperty("valid", false);
                response.addProperty("error", e.getMessage());
                response.addProperty("code", e.getCode());
            }
            sendJson(exchange, 200, response);
        }

        private void createOrUpdate(HttpExchange exchange, String pathId) throws IOException {
            String body = readBody(exchange);
            com.google.gson.JsonElement element;
            try {
                element = GSON.fromJson(body, com.google.gson.JsonElement.class);
            } catch (RuntimeException e) {
                throw new ApiRequestException("Malformed JSON: " + e.getMessage());
            }
            if (element == null || !element.isJsonObject()) {
                throw new ApiRequestException("Request body must be a JSON object");
            }
            com.google.gson.JsonObject json = element.getAsJsonObject();
            String id = pathId != null ? pathId
                    : (json.has("id") && !json.get("id").isJsonNull()
                            ? json.get("id").getAsString() : null);
            if (id == null || !id.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_/.-]+")) {
                throw new ApiRequestException("Invalid or missing function id");
            }
            String format = json.has("format") && !json.get("format").isJsonNull()
                    ? json.get("format").getAsString() : "json";
            String source = json.has("source") && !json.get("source").isJsonNull()
                    ? json.get("source").getAsString()
                    : (json.has("definition") && !json.get("definition").isJsonNull()
                            ? json.get("definition").toString() : null);
            if (source == null) {
                throw new ApiRequestException("Provide 'source' or 'definition'");
            }
            if ("definition".equalsIgnoreCase(format) || json.has("definition")
                    && !json.has("source")) {
                format = "json";
            }
            String fileName = "mcfunction".equalsIgnoreCase(format) ? "fn.mcfunction"
                    : "fn.json";
            com.commandapi.functions.FunctionDefinition definition;
            try {
                definition = com.commandapi.functions.FunctionParser.parse(id, fileName,
                        source, "api");
                com.commandapi.functions.FunctionValidator.validate(definition, functionLimits);
            } catch (com.commandapi.functions.FunctionException e) {
                sendError(exchange, 400, e.getMessage());
                return;
            }
            boolean existed = functionRegistry.get(id) != null;
            if (configDir == null) {
                sendError(exchange, 503, "Function storage unavailable");
                return;
            }
            try {
                String stored = "mcfunction".equalsIgnoreCase(format) ? source
                        : GSON.toJson(GSON.fromJson(source,
                                com.google.gson.JsonElement.class));
                com.commandapi.functions.FunctionStorage.writeJson(configDir,
                        definition.getId(), stored);
            } catch (Exception e) {
                sendError(exchange, 500, "Write failed: " + e.getMessage());
                return;
            }
            functionRegistry.replace(definition);
            sendFunctionDefinition(exchange, existed ? 200 : 201, definition);
        }

        private void runOrPreview(HttpExchange exchange, String id, boolean preview)
                throws IOException {
            com.commandapi.functions.FunctionDefinition definition =
                    functionRegistry.get(id);
            if (definition == null) {
                sendError(exchange, 404, "Function not found: " + id);
                return;
            }
            Object[] parsed = readFunctionArgs(readBody(exchange));
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> args =
                    (java.util.Map<String, Object>) parsed[0];
            boolean async = (Boolean) parsed[1];
            @SuppressWarnings("unchecked")
            java.util.List<Object> positional =
                    (java.util.List<Object>) parsed[2];
            com.commandapi.functions.FunctionExecutionPlan plan;
            try {
                if (positional != null) {
                    java.util.Map<String, Object> named =
                            new java.util.LinkedHashMap<String, Object>();
                    java.util.List<String> names = new java.util.ArrayList<String>(
                            definition.getParameters().keySet());
                    if (positional.size() > names.size()) {
                        throw new com.commandapi.functions.FunctionException(
                                "Too many positional arguments", "TOO_MANY_ARGUMENTS");
                    }
                    for (int i = 0; i < positional.size(); i++) {
                        named.put(names.get(i), positional.get(i));
                    }
                    args = named;
                }
                plan = jobs.preview(id, args);
            } catch (com.commandapi.functions.FunctionException e) {
                int status = "UNKNOWN_FUNCTION".equals(e.getCode()) ? 404
                        : "COMMAND_DENIED".equals(e.getCode()) ? 403 : 400;
                sendError(exchange, status, e.getMessage());
                return;
            }
            if (preview) {
                if (!bridge.isInWorld()) {
                    sendError(exchange, 503, "Session unavailable");
                    return;
                }
                com.google.gson.JsonObject response = new com.google.gson.JsonObject();
                response.addProperty("function", id);
                response.add("args", GSON.toJsonTree(plan.getArgs()));
                com.google.gson.JsonArray commands = new com.google.gson.JsonArray();
                for (String command : plan.commands()) {
                    commands.add(command);
                }
                response.add("commands", commands);
                response.add("steps", GSON.toJsonTree(plan.getStructuredSteps()));
                response.addProperty("total_steps", plan.getItems().size());
                sendJson(exchange, 200, response);
                return;
            }
            if (!bridge.isInWorld()) {
                sendError(exchange, 503, "Session unavailable");
                return;
            }
            try {
                if (async) {
                    com.commandapi.functions.FunctionExecutor.Work work =
                            jobs.submitFunction(id, args);
                    if (work == null) {
                        sendError(exchange, 429, "Job capacity reached");
                        return;
                    }
                    exchange.getResponseHeaders().set("Location", "/api/jobs/" + work.id());
                    sendJson(exchange, 202, work.snapshot());
                } else {
                    com.commandapi.functions.FunctionExecutor.Work work =
                            jobs.runFunctionSync(id, args);
                    if (work == null) {
                        sendError(exchange, 429, "Execution queue full");
                        return;
                    }
                    exchange.getResponseHeaders().set("Location", "/api/jobs/" + work.id());
                    sendJson(exchange, 200, work.snapshot());
                }
            } catch (com.commandapi.functions.FunctionException e) {
                int status = "NO_SESSION".equals(e.getCode()) ? 503
                        : "COMMAND_DENIED".equals(e.getCode()) ? 403 : 400;
                sendError(exchange, status, e.getMessage());
            }
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
