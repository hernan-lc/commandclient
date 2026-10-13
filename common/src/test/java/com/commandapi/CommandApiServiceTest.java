package com.commandapi;

import com.commandapi.config.ApiConfig;
import com.commandapi.config.ConfigLoader;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.ServerSocket;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import com.google.gson.JsonParser;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises restart, persistence and discovery against a real socket. */
class CommandApiServiceTest {

    private static final class FakeBridge implements MinecraftBridge {
        @Override
        public boolean isInWorld() {
            return true;
        }

        @Override
        public String getPlayerName() {
            return "Steve";
        }

        @Override
        public ChatResult sendChat(String text) {
            return ChatResult.ok(text.startsWith("/") ? "Command submitted" : "Message sent to chat");
        }
    }

    private static String joined(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    @Test
    void ephemeralStartWritesAddressFile(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            assertTrue(service.isRunning());
            int port = service.getHttpServerManager().getPort();
            assertTrue(port > 0, "ephemeral port must resolve to a real port");
            Path address = dir.resolve(ConfigLoader.ADDRESS_FILE_NAME);
            assertTrue(Files.isRegularFile(address));
            String body = new String(Files.readAllBytes(address), StandardCharsets.UTF_8);
            assertTrue(body.contains(String.valueOf(port)));
        } finally {
            service.stop();
        }
        assertFalse(Files.exists(dir.resolve(ConfigLoader.ADDRESS_FILE_NAME)));
    }

    @Test
    void commandChangesPortAndPersists(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            int first = service.getHttpServerManager().getPort();
            assertTrue(first > 0);

            String text = joined(service.runCommand("port 0"));
            assertTrue(text.contains("automatic") || text.contains("Serving at"));
            assertTrue(service.isRunning());
            int second = service.getHttpServerManager().getPort();
            assertTrue(second > 0);
            assertEquals(second, addressPort(dir));

            ApiConfig saved = ConfigLoader.load(dir);
            assertTrue(saved.isEphemeral());

            assertTrue(joined(service.runCommand("status")).contains("Running"));
            assertTrue(joined(service.runCommand("restart")).contains("Server restarted"));
            assertEquals(service.getHttpServerManager().getPort(), addressPort(dir));
        } finally {
            service.stop();
        }
    }

    @Test
    void loginTogglePersistsWithoutRestart(@TempDir Path dir) {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            String address = service.getServerAddress();
            assertTrue(joined(service.runCommand("login off")).contains("disabled"));
            assertTrue(service.isRunning());
            assertEquals(address, service.getServerAddress(),
                    "toggle must not restart the server or re-roll the port");
            assertTrue(joined(service.runCommand("status")).contains("login=off"));
            assertFalse(ConfigLoader.load(dir).isLoginSummary());
        } finally {
            service.stop();
        }
    }

    @Test
    void occupiedExplicitPortFailsCleanly(@TempDir Path dir) throws Exception {
        CommandApiService first = new CommandApiService(dir, new FakeBridge(), "test", "test");
        first.start();
        int taken = 0;
        try {
            taken = first.getHttpServerManager().getPort();
            ConfigLoader.save(dir, new ApiConfig("127.0.0.1", taken, "", false));
            CommandApiService second = new CommandApiService(dir, new FakeBridge(), "test", "test");
            second.start();
            try {
                assertFalse(second.isRunning(), "second server on the same port must not run");
            } finally {
                second.stop();
            }
        } finally {
            first.stop();
        }
        assertTrue(taken > 0);
    }

    private static int addressPort(Path dir) throws Exception {
        String body = new String(Files.readAllBytes(dir.resolve(ConfigLoader.ADDRESS_FILE_NAME)), StandardCharsets.UTF_8);
        return new JsonParser().parse(body).getAsJsonObject().get("port").getAsInt();
    }

    private static int statusCode(int port) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/api/status").openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void failedPortChangeRestoresServerConfigAndAddress(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try (ServerSocket occupied = new ServerSocket(0)) {
            int oldPort = service.getHttpServerManager().getPort();
            ApiConfig oldConfig = service.getConfig();
            assertFalse(service.applyAndRestart(new ApiConfig("127.0.0.1", occupied.getLocalPort(), "", false)));
            assertTrue(service.isRunning());
            assertEquals(oldConfig.getPort(), service.getConfig().getPort());
            assertEquals(oldConfig.getPort(), ConfigLoader.load(dir).getPort());
            int restoredPort = service.getHttpServerManager().getPort();
            assertEquals(restoredPort, addressPort(dir));
            assertEquals(401, statusCode(restoredPort));
            assertTrue(oldPort > 0);
        } finally {
            service.stop();
        }
    }

    @Test
    void successfulRestartUpdatesAddressFile(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            int firstPort = service.getHttpServerManager().getPort();
            try (ServerSocket reservation = new ServerSocket(0)) {
                int nextPort = reservation.getLocalPort();
                reservation.close();
                assertTrue(service.applyAndRestart(new ApiConfig("127.0.0.1", nextPort, "", false)));
                assertEquals(nextPort, addressPort(dir));
                assertEquals(nextPort, ConfigLoader.load(dir).getPort());
                assertEquals(200, statusCode(nextPort));
                assertTrue(firstPort > 0);
            }
        } finally {
            service.stop();
        }
        assertFalse(Files.exists(dir.resolve(ConfigLoader.ADDRESS_FILE_NAME)));
    }

    @Test
    void httpCommandResponseSaysSubmitted(@TempDir Path dir) throws Exception {
        CommandApiService service = new CommandApiService(dir, new FakeBridge(), "test", "test");
        service.start();
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:"
                    + service.getHttpServerManager().getPort() + "/api/chat").openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + service.getConfig().getToken());
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            byte[] body = "{\"text\":\"/time set day\"}".getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            assertEquals(200, connection.getResponseCode());
            byte[] response = new byte[1024];
            int count = connection.getInputStream().read(response);
            String text = new String(response, 0, count, StandardCharsets.UTF_8);
            assertTrue(text.contains("Command submitted"));
            assertFalse(text.contains("Command executed"));
            connection.disconnect();
        } finally {
            service.stop();
        }
    }
}
