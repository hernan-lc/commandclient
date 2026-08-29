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
    public static final int DEFAULT_PORT = 8080;

    private final int port;
    private final String host;
    private final String token;
    private final boolean authEnabled;

    public ApiConfig(String host, int port, String token, boolean authEnabled) {
        this.host = host == null ? DEFAULT_HOST : host;
        this.port = port;
        this.token = token == null ? "" : token;
        this.authEnabled = authEnabled;
    }

    public static ApiConfig defaults() {
        return new ApiConfig(DEFAULT_HOST, DEFAULT_PORT, "", false);
    }

    public int getPort() {
        return port;
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
