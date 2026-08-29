package com.commandapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void defaultsToLoopbackWithoutAuth() {
        ApiConfig config = ConfigLoader.load(null);
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals("", config.getToken());
        assertFalse(config.isAuthEnabled());
        assertFalse(config.isExposedBeyondLoopback());
    }

    @Test
    void readsConfigFile(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve(ConfigLoader.CONFIG_FILE_NAME),
                ("{\"port\":9000,\"host\":\"0.0.0.0\",\"token\":\"t\",\"authEnabled\":true}")
                        .getBytes(StandardCharsets.UTF_8));

        ApiConfig config = ConfigLoader.load(dir);
        assertEquals(9000, config.getPort());
        assertEquals("0.0.0.0", config.getHost());
        assertEquals("t", config.getToken());
        assertTrue(config.isAuthEnabled());
        assertTrue(config.isExposedBeyondLoopback());
    }

    @Test
    void missingFieldsFallBackToDefaults() {
        ApiConfig config = ConfigLoader.parse("{\"port\":9001}");
        assertEquals(9001, config.getPort());
        assertEquals("127.0.0.1", config.getHost());
        assertFalse(config.isAuthEnabled());
    }

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path dir) {
        assertEquals(8080, ConfigLoader.load(dir).getPort());
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve(ConfigLoader.CONFIG_FILE_NAME), "{not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(8080, ConfigLoader.load(dir).getPort());
    }

    @Test
    void systemPropertyOverridesDefaultButNotFile() {
        System.setProperty("api.port", "9100");
        try {
            assertEquals(9100, ConfigLoader.parse("{}").getPort());
            assertEquals(9200, ConfigLoader.parse("{\"port\":9200}").getPort());
        } finally {
            System.clearProperty("api.port");
        }
    }

    @Test
    void toStringNeverLeaksTheToken() {
        assertFalse(new ApiConfig("127.0.0.1", 8080, "super-secret", true).toString().contains("super-secret"));
    }
}
