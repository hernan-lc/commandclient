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

    private final int port;
    private final String host;
    private final String token;
    private final boolean authEnabled;

    public ApiConfig(String host, int port, String token, boolean authEnabled) {
        this.host = host == null ? DEFAULT_HOST : host;
        this.port = normalizePort(port);
        this.token = token == null ? "" : token;
        this.authEnabled = authEnabled;
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
        return new ApiConfig(DEFAULT_HOST, DEFAULT_PORT, "", false);
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

    /** True when the server binds an interface other than loopback. */
    public boolean isExposedBeyondLoopback() {
        return !("127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host));
    }

    /** Never includes the token. */
    @Override
    public String toString() {
        return "ApiConfig{host=" + host + ", port=" + port
                + ", authEnabled=" + authEnabled
                + ", tokenConfigured=" + !token.isEmpty() + "}";
    }
}
