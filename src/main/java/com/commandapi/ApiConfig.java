package com.commandapi;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;

/**
 * Configuration class for the Command API mod.
 * Loads settings from config file, gradle.properties, or system properties.
 */
public class ApiConfig {
    private final int port;
    private final String token;
    private final boolean authEnabled;
    private final String host;

    // Default values
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_HOST = "0.0.0.0";

    public ApiConfig() {
        // First, try to load from config file
        JsonObject fileConfig = loadConfigFile();

        // Port: file config > system property > default
        if (fileConfig != null && fileConfig.has("port")) {
            this.port = fileConfig.get("port").getAsInt();
        } else {
            this.port = Integer.parseInt(System.getProperty("api.port", String.valueOf(DEFAULT_PORT)));
        }

        // Host: file config > system property > default
        if (fileConfig != null && fileConfig.has("host")) {
            this.host = fileConfig.get("host").getAsString();
        } else {
            this.host = System.getProperty("api.host", DEFAULT_HOST);
        }

        // Token: file config > system property > default
        if (fileConfig != null && fileConfig.has("token")) {
            this.token = fileConfig.get("token").getAsString();
        } else {
            this.token = System.getProperty("api.token", "");
        }

        // Auth enabled: file config > system property > default
        if (fileConfig != null && fileConfig.has("authEnabled")) {
            this.authEnabled = fileConfig.get("authEnabled").getAsBoolean();
        } else {
            this.authEnabled = Boolean.parseBoolean(System.getProperty("api.auth.enabled", "false"));
        }
    }

    private JsonObject loadConfigFile() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("commandapi.json");
            File configFile = configPath.toFile();

            if (configFile.exists()) {
                Gson gson = new Gson();
                try (Reader reader = new FileReader(configFile)) {
                    return gson.fromJson(reader, JsonObject.class);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load config file: " + e.getMessage());
        }
        return null;
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

    public void logConfig() {
        System.out.println("API Config - Host: " + host + ", Port: " + port + ", Auth Enabled: " + authEnabled);
    }
}
