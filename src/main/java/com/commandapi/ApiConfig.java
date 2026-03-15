package com.commandapi;

/**
 * Configuration class for the Command API mod.
 * Loads settings from gradle.properties or system properties.
 */
public class ApiConfig {
    private final int port;
    private final String token;
    private final boolean authEnabled;
    
    public ApiConfig() {
        this.port = Integer.parseInt(System.getProperty("api.port", "8080"));
        this.token = System.getProperty("api.token", "");
        this.authEnabled = Boolean.parseBoolean(System.getProperty("api.auth.enabled", "false"));
    }
    
    public int getPort() {
        return port;
    }
    
    public String getToken() {
        return token;
    }
    
    public boolean isAuthEnabled() {
        return authEnabled;
    }
    
    public void logConfig() {
        System.out.println("API Config - Port: " + port + ", Auth Enabled: " + authEnabled);
    }
}
