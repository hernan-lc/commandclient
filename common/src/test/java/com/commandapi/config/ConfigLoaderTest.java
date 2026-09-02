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
        assertEquals(0, config.getPort());
        assertTrue(config.isEphemeral());
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
        assertEquals(0, ConfigLoader.load(dir).getPort());
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve(ConfigLoader.CONFIG_FILE_NAME), "{not json".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, ConfigLoader.load(dir).getPort());
    }

    @Test
    void outOfRangePortFallsBackToEphemeral() {
        assertEquals(0, ConfigLoader.parse("{\"port\":99999}").getPort());
        assertEquals(0, ConfigLoader.parse("{\"port\":-1}").getPort());
        assertEquals(1234, ConfigLoader.parse("{\"port\":1234}").getPort());
    }

    @Test
    void wrongTypePortFallsBackToDefault() {
        assertEquals(0, ConfigLoader.parse("{\"port\":\"abc\"}").getPort());
        assertEquals(0, ConfigLoader.parse("{\"port\":true}").getPort());
    }

    @Test
    void saveRoundTrips(@TempDir Path dir) {
        assertTrue(ConfigLoader.save(dir, new ApiConfig("0.0.0.0", 9123, "s3cret", true)));
        ApiConfig reloaded = ConfigLoader.load(dir);
        assertEquals("0.0.0.0", reloaded.getHost());
        assertEquals(9123, reloaded.getPort());
        assertEquals("s3cret", reloaded.getToken());
        assertTrue(reloaded.isAuthEnabled());
    }

    @Test
    void addressFileRoundTrips(@TempDir Path dir) throws IOException {
        ConfigLoader.writeAddress(dir, "127.0.0.1", 51234);
        Path address = dir.resolve(ConfigLoader.ADDRESS_FILE_NAME);
        assertTrue(Files.isRegularFile(address));
        String body = new String(Files.readAllBytes(address), StandardCharsets.UTF_8);
        assertTrue(body.contains("51234"));
        assertTrue(body.contains("http://127.0.0.1:51234"));
        ConfigLoader.deleteAddress(dir);
        assertFalse(Files.exists(address));
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
