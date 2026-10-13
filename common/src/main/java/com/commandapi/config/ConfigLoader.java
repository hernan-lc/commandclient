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
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;

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
                    throw new IllegalArgumentException("Invalid API configuration", e);
                }
            }
        }
        ApiConfig config = fromJson(json);
        if (configDir != null && !save(configDir, config)) throw new IllegalStateException("Cannot securely persist API configuration");
        return config;
    }

    /** file value > system property > default, per field. Never throws. */
    public static ApiConfig fromJson(JsonObject json) {
        String host = string(json, "host", "api.host", ApiConfig.DEFAULT_HOST);
        int port = integer(json, "port", "api.port", ApiConfig.DEFAULT_PORT);
        String token = string(json, "token", "api.token", "");
        boolean authEnabled = bool(json, "authEnabled", "api.auth.enabled", true);
        if (token.isEmpty()) {
            if (has(json, "token") && has(json, "authEnabled") && json.get("authEnabled").getAsBoolean())
                throw new IllegalArgumentException("Authentication enabled with empty token");
            token = ApiConfig.newToken();
            authEnabled = true;
        }
        boolean loginSummary = bool(json, "loginSummary", "api.login.summary", true);
        java.util.List<String> allowed = new java.util.ArrayList<>();
        if (has(json, "allowedCommands")) {
            for (com.google.gson.JsonElement value : json.getAsJsonArray("allowedCommands")) allowed.add(value.getAsString());
        } else allowed.add("*");
        return new ApiConfig(host, port, token, authEnabled, loginSummary,
                bool(json, "allowRemotePrivileged", "api.remote.privileged", false),
                bool(json, "elevatedSingleplayer", "api.singleplayer.elevated", true), allowed,
                limit(json, "maxJobs", 128), limit(json, "maxQueuedCommands", 1024),
                limit(json, "commandsPerTick", 16), limit(json, "retentionSeconds", 600),
                limit(json, "httpWorkers", 8), limit(json, "maxHttpQueue", 64),
                limit(json, "executionTimeoutSeconds", 30), limit(json, "jobConcurrency", 1));
    }

    /** Parses a config document from raw JSON text. Never throws. */
    public static ApiConfig parse(String jsonText) {
        return fromJson(GSON.fromJson(jsonText, JsonObject.class));
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
            json.addProperty("loginSummary", config.isLoginSummary());
            json.addProperty("allowRemotePrivileged", config.isAllowRemotePrivileged());
            json.addProperty("elevatedSingleplayer", config.isElevatedSingleplayer());
            json.add("allowedCommands", GSON.toJsonTree(config.getCommandPolicy().commands()));
            json.addProperty("maxJobs", config.getMaxJobs());
            json.addProperty("maxQueuedCommands", config.getMaxQueuedCommands());
            json.addProperty("commandsPerTick", config.getCommandsPerTick());
            json.addProperty("retentionSeconds", config.getRetentionSeconds());
            json.addProperty("httpWorkers", config.getHttpWorkers());
            json.addProperty("maxHttpQueue", config.getMaxHttpQueue());
            json.addProperty("executionTimeoutSeconds", config.getExecutionTimeoutSeconds());
            json.addProperty("jobConcurrency", config.getJobConcurrency());
            Path configPath = configDir.resolve(CONFIG_FILE_NAME);
            writeJson(configPath, json);
            securePermissions(configPath);
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[CommandAPI] Failed to write config: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Records the address that was actually bound, for ephemeral-port
     * discovery. Best effort: failures are logged, never thrown.
     */
    public static boolean writeAddress(Path configDir, String host, int port) {
        if (configDir == null) {
            return true;
        }
        try {
            Files.createDirectories(configDir);
            JsonObject json = new JsonObject();
            json.addProperty("host", host);
            json.addProperty("port", port);
            json.addProperty("url", "http://" + host + ":" + port);
            Path addressPath = configDir.resolve(ADDRESS_FILE_NAME);
            writeJson(addressPath, json);
            return true;
        } catch (IOException | RuntimeException e) {
            System.err.println("[CommandAPI] Failed to write " + ADDRESS_FILE_NAME + ": " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static void writeJson(Path target, JsonObject json) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            securePermissions(temporary);
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                PRETTY_GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
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
            System.err.println("[CommandAPI] Failed to delete " + ADDRESS_FILE_NAME + ": " + e.getClass().getSimpleName());
        }
    }

    private static void securePermissions(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
    private static int limit(JsonObject json, String key, int fallback) {
        if (!has(json, key)) return fallback;
        return json.get(key).getAsInt();
    }

    private static String string(JsonObject json, String key, String property, String fallback) {
        try {
            if (has(json, key)) {
                return json.get(key).getAsString();
            }
            return System.getProperty(property, fallback);
        } catch (RuntimeException e) {
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getClass().getSimpleName());
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
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getClass().getSimpleName());
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
            System.err.println("[CommandAPI] Invalid value for \"" + key + "\", using default: " + e.getClass().getSimpleName());
            return fallback;
        }
    }

    private static boolean has(JsonObject json, String key) {
        return json != null && json.has(key) && !json.get(key).isJsonNull();
    }
}
