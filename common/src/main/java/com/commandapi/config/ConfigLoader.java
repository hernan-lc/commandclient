package com.commandapi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@link ApiConfig} from {@code <configDir>/commandapi.json}, falling back
 * to system properties and then to the defaults.
 *
 * <p>The config directory is injected by the caller (the version module passes
 * Fabric's config dir) so that this class stays free of Fabric APIs.</p>
 */
public final class ConfigLoader {

    public static final String CONFIG_FILE_NAME = "commandapi.json";

    /**
     * Written next to the config file on every successful start with the
     * address that was actually bound. This is how external tools find the
     * server when the port is ephemeral (the default): read this file instead
     * of assuming a fixed port.
     */
    public static final String ADDRESS_FILE_NAME = "commandapi-address.json";

    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigLoader() {
    }

    /** Loads the config file from {@code configDir}, if present. */
    public static ApiConfig load(Path configDir) {
        JsonObject json = null;
        if (configDir != null) {
            Path configPath = configDir.resolve(CONFIG_FILE_NAME);
            if (Files.isRegularFile(configPath)) {
                try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                    json = GSON.fromJson(reader, JsonObject.class);
                } catch (IOException | RuntimeException e) {
                    System.err.println("[CommandAPI] Failed to read " + configPath + ": " + e.getMessage());
                }
            }
        }
        return fromJson(json);
    }

    /** file value > system property > default, per field. Never throws. */
    public static ApiConfig fromJson(JsonObject json) {
        String host = string(json, "host", "api.host", ApiConfig.DEFAULT_HOST);
        int port = integer(json, "port", "api.port", ApiConfig.DEFAULT_PORT);
        String token = string(json, "token", "api.token", "");
        boolean authEnabled = bool(json, "authEnabled", "api.auth.enabled", false);
        return new ApiConfig(host, port, token, authEnabled);
    }

    /** Parses a config document from raw JSON text. Never throws. */
    public static ApiConfig parse(String jsonText) {
        try {
            return fromJson(GSON.fromJson(jsonText, JsonObject.class));
        } catch (RuntimeException e) {
            System.err.println("[CommandAPI] Failed to parse config, using defaults: " + e.getMessage());
            return ApiConfig.defaults();
        }
    }

    /**
     * Persists the config to {@code <configDir>/commandapi.json}, creating the
     * directory when needed. Used by the in-game {@code /commandapi} commands
     * so a change survives restarts. Best effort: failures are logged and
     * reported via the return value, never thrown.
     *
     * @return true when the file was written.
     */
    public static boolean save(Path configDir, ApiConfig config) {
        if (configDir == null || config == null) {
            return false;
        }
        try {
            Files.createDirectories(configDir);
            JsonObject json = new JsonObject();
            json.addProperty("host", config.getHost());
            json.addProperty("port", config.getPort());
            json.addProperty("token", config.getToken());
            json.addProperty("authEnabled", config.isAuthEnabled());
            Path configPath = configDir.resolve(CONFIG_FILE_NAME);
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                PRETTY_GSON.toJson(json, writer);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[CommandAPI] Failed to write config: " + e.getMessage());
            return false;
        }
    }

    /**
     * Records the address that was actually bound, for ephemeral-port
     * discovery. Best effort: failures are logged, never thrown.
     */
    public static void writeAddress(Path configDir, String host, int port) {
        if (configDir == null) {
            return;
        }
        try {
            Files.createDirectories(configDir);
            JsonObject json = new JsonObject();
            json.addProperty("host", host);
            json.addProperty("port", port);
            json.addProperty("url", "http://" + host + ":" + port);
            Path addressPath = configDir.resolve(ADDRESS_FILE_NAME);
            try (Writer writer = Files.newBufferedWriter(addressPath, StandardCharsets.UTF_8)) {
                PRETTY_GSON.toJson(json, writer);
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[CommandAPI] Failed to write " + ADDRESS_FILE_NAME + ": " + e.getMessage());
        }
    }

    /** Removes a stale address file, e.g. when the server stops. Best effort. */
    public static void deleteAddress(Path configDir) {
        if (configDir == null) {
            return;
        }
        try {
            Files.deleteIfExists(configDir.resolve(ADDRESS_FILE_NAME));
        } catch (IOException | RuntimeException e) {
            System.err.println("[CommandAPI] Failed to delete " + ADDRESS_FILE_NAME + ": " + e.getMessage());
        }
    }

    private static String string(JsonObject json, String key, String property, String fallback) {
        try {
            if (has(json, key)) {
                return json.get(key).getAsString();
            }
            return System.getProperty(property, fallback);
        } catch (RuntimeException e) {
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getMessage());
            return fallback;
        }
    }

    private static int integer(JsonObject json, String key, String property, int fallback) {
        try {
            if (has(json, key)) {
                return ApiConfig.normalizePort(json.get(key).getAsInt());
            }
            return ApiConfig.normalizePort(
                    Integer.parseInt(System.getProperty(property, String.valueOf(fallback))));
        } catch (RuntimeException e) {
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getMessage());
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, String property, boolean fallback) {
        try {
            if (has(json, key)) {
                return json.get(key).getAsBoolean();
            }
            return Boolean.parseBoolean(System.getProperty(property, String.valueOf(fallback)));
        } catch (RuntimeException e) {
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getMessage());
            return fallback;
        }
    }

    private static boolean has(JsonObject json, String key) {
        return json != null && json.has(key) && !json.get(key).isJsonNull();
    }
}
