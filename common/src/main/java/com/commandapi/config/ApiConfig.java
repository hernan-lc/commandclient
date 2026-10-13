package com.commandapi.config;

/**
 * Immutable runtime configuration for the API server.
 *
 * <p>Build instances with {@link ConfigLoader}; this class holds no I/O and no
 * Minecraft or Fabric dependency so it can be unit tested directly.</p>
 */
public final class ApiConfig {

    /** Loopback by default: the API is only reachable from this machine. */
    public static final String DEFAULT_HOST = "127.0.0.1";
    /**
     * Ephemeral by default: the OS picks a free port at startup, so two
     * clients (or a stale process holding an old port) can never collide.
     * The bound port is printed in the log, answered by {@code GET /api/status}
     * and written to {@code commandapi-address.json} next to the config file.
     * Set an explicit port only when a fixed address matters to you.
     */
    public static final int DEFAULT_PORT = 0;

    /** Lowest and highest valid TCP port, inclusive. 0 means "pick one for me". */
    public static final int MIN_PORT = 0;
    public static final int MAX_PORT = 65535;

    private final boolean allowRemotePrivileged;
    private final boolean elevatedSingleplayer;
    private final com.commandapi.security.CommandPolicy commandPolicy;
    private final int maxJobs, maxQueuedCommands, commandsPerTick, retentionSeconds;
    private final int httpWorkers;
    private final int maxHttpQueue;
    private final int executionTimeoutSeconds;
    private final int jobConcurrency;
    private final int port;
    private final String host;
    private final String token;
    private final boolean authEnabled;
    private final boolean loginSummary;

    public ApiConfig(String host, int port, String token, boolean authEnabled) {
        this(host, port, token, authEnabled, true);
    }

    public ApiConfig(String host, int port, String token, boolean authEnabled, boolean loginSummary) {
        this(host, port, token, authEnabled, loginSummary, false, true,
                java.util.Collections.singleton("*"), 128, 1024, 16, 600, 8, 64, 30, 1);
    }

    public ApiConfig(String host, int port, String token, boolean authEnabled, boolean loginSummary,
                     boolean allowRemotePrivileged, boolean elevatedSingleplayer, java.util.Collection<String> allowed,
                     int maxJobs, int maxQueuedCommands, int commandsPerTick, int retentionSeconds,
                     int httpWorkers, int maxHttpQueue, int executionTimeoutSeconds, int jobConcurrency) {
        if (authEnabled && (token == null || token.trim().isEmpty())) throw new IllegalArgumentException("Authentication requires a nonempty token");
        if (maxJobs < 1 || maxJobs > 4096 || maxQueuedCommands < 1 || maxQueuedCommands > 65536
                || commandsPerTick < 1 || commandsPerTick > 1024 || retentionSeconds < 1 || retentionSeconds > 86400
                || httpWorkers < 1 || httpWorkers > 128 || maxHttpQueue < 1 || maxHttpQueue > 4096
                || executionTimeoutSeconds < 1 || executionTimeoutSeconds > 300 || jobConcurrency < 1 || jobConcurrency > 8)
            throw new IllegalArgumentException("Execution limits out of range");
        this.allowRemotePrivileged = allowRemotePrivileged;
        this.elevatedSingleplayer = elevatedSingleplayer;
        this.commandPolicy = new com.commandapi.security.CommandPolicy(allowed);
        this.maxJobs = maxJobs; this.maxQueuedCommands = maxQueuedCommands; this.commandsPerTick = commandsPerTick;
        this.retentionSeconds = retentionSeconds; this.httpWorkers = httpWorkers; this.maxHttpQueue = maxHttpQueue;
        this.executionTimeoutSeconds = executionTimeoutSeconds; this.jobConcurrency = jobConcurrency;
        this.host = host == null ? DEFAULT_HOST : host;
        this.port = normalizePort(port);
        this.token = token == null ? "" : token;
        this.authEnabled = authEnabled;
        this.loginSummary = loginSummary;
    }

    /** Out-of-range ports fall back to ephemeral instead of failing to bind. */
    public static int normalizePort(int port) {
        if (port < MIN_PORT || port > MAX_PORT) {
            return DEFAULT_PORT;
        }
        return port;
    }

    /** True for any integer in {@code [0, 65535]}. */
    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    public static ApiConfig defaults() {
        return new ApiConfig(DEFAULT_HOST, DEFAULT_PORT, newToken(), true);
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public boolean isAllowRemotePrivileged() { return allowRemotePrivileged; }
    public boolean isElevatedSingleplayer() { return elevatedSingleplayer; }
    public com.commandapi.security.CommandPolicy getCommandPolicy() { return commandPolicy; }
    public int getMaxJobs() { return maxJobs; }
    public int getMaxQueuedCommands() { return maxQueuedCommands; }
    public int getCommandsPerTick() { return commandsPerTick; }
    public int getRetentionSeconds() { return retentionSeconds; }
    public int getHttpWorkers() { return httpWorkers; }
    public int getMaxHttpQueue() { return maxHttpQueue; }
    public int getExecutionTimeoutSeconds() { return executionTimeoutSeconds; }
    public int getJobConcurrency() { return jobConcurrency; }
    public ApiConfig copy(String host, int port, String token, boolean auth, boolean login) {
        return new ApiConfig(host, port, token, auth, login, allowRemotePrivileged, elevatedSingleplayer,
                commandPolicy.commands(), maxJobs, maxQueuedCommands, commandsPerTick, retentionSeconds,
                httpWorkers, maxHttpQueue, executionTimeoutSeconds, jobConcurrency);
    }

    public int getPort() {
        return port;
    }

    /** True when the OS picks the port at startup (the default). */
    public boolean isEphemeral() {
        return port == DEFAULT_PORT;
    }

    public String getHost() {
        return host;
    }

    public String getToken() {
        return token;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    /** When true (the default), joining a world prints a short config summary in chat. */
    public boolean isLoginSummary() {
        return loginSummary;
    }

    /** True when the server binds an interface other than loopback. */
    public boolean isExposedBeyondLoopback() {
        return !("127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host));
    }

    /** Never includes the token. */
    @Override
    public String toString() {
        return "ApiConfig{host=" + host + ", port=" + port
                + ", authEnabled=" + authEnabled
                + ", tokenConfigured=" + !token.isEmpty()
                + ", loginSummary=" + loginSummary + "}";
    }
}
