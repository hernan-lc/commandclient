package com.commandapi.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
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

    private static final Gson GSON = new Gson();

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

    /** file value > system property > default, per field. */
    public static ApiConfig fromJson(JsonObject json) {
        return new ApiConfig(
                string(json, "host", "api.host", ApiConfig.DEFAULT_HOST),
                integer(json, "port", "api.port", ApiConfig.DEFAULT_PORT),
                string(json, "token", "api.token", ""),
                bool(json, "authEnabled", "api.auth.enabled", false));
    }

    /** Parses a config document from raw JSON text. */
    public static ApiConfig parse(String jsonText) {
        return fromJson(GSON.fromJson(jsonText, JsonObject.class));
    }

    private static String string(JsonObject json, String key, String property, String fallback) {
        if (has(json, key)) {
            return json.get(key).getAsString();
        }
        return System.getProperty(property, fallback);
    }

    private static int integer(JsonObject json, String key, String property, int fallback) {
        if (has(json, key)) {
            return json.get(key).getAsInt();
        }
        try {
            return Integer.parseInt(System.getProperty(property, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean bool(JsonObject json, String key, String property, boolean fallback) {
        if (has(json, key)) {
            return json.get(key).getAsBoolean();
        }
        return Boolean.parseBoolean(System.getProperty(property, String.valueOf(fallback)));
    }

    private static boolean has(JsonObject json, String key) {
        return json != null && json.has(key) && !json.get(key).isJsonNull();
    }
}
