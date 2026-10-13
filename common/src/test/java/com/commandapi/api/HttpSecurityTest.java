package com.commandapi.api;
import com.commandapi.config.*;
import com.commandapi.minecraft.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HttpSecurityTest {
    HttpServerManager server;
    int sent;
    @AfterEach void stop() { if (server != null) server.stop(); }
    void start() {
        MinecraftBridge bridge = new MinecraftBridge() {
            public boolean isInWorld() { return true; }
            public String getPlayerName() { return "player"; }
            public ChatResult sendChat(String text) { sent++; return ChatResult.ok("submitted"); }
            public String executionMode() { return "singleplayer-native"; }
        };
        server = new HttpServerManager(new ApiConfig("127.0.0.1", 0, "secret", false, true,
                false, true, Arrays.asList("time"), 128, 1024, 16, 600, 8, 64, 30, 1), bridge, "test", "test");
        assertTrue(server.start());
    }
    int request(String path, String token, String origin, String type, String command) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:" + server.getPort() + path).openConnection();
        c.setRequestMethod("POST"); c.setDoOutput(true);
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        if (origin != null) c.setRequestProperty("Origin", origin);
        if (type != null) c.setRequestProperty("Content-Type", type);
        c.getOutputStream().write(("{\"text\":\"" + command + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        int status = c.getResponseCode(); c.disconnect(); return status;
    }
    @Test void privilegedExecutionRequiresTokenAndPolicy() throws Exception {
        start();
        assertEquals(401, request("/api/chat", null, null, "application/json", "/time set day"));
        assertEquals(401, request("/api/jobs", "wrong", null, "application/json", "/time set day"));
        assertEquals(403, request("/api/chat", "secret", null, "application/json", "/op player"));
        assertEquals(200, request("/api/chat", "secret", null, "application/json", "/time set day"));
        assertEquals(1, sent);
    }
    @Test void rejectsBrowserOriginsTypesAndPrefixPaths() throws Exception {
        start();
        // HttpURLConnection strips Origin by default, so use Java's raw socket to test actual wire headers.
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            String body = "{\"text\":\"/time set day\"}";
            String request = "POST /api/chat HTTP/1.1\r\nHost: localhost\r\nOrigin: http://localhost:9999\r\nAuthorization: Bearer secret\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream())).readLine().contains("403"));
        }
        assertEquals(415, request("/api/chat", "secret", null, "text/plain", "/time set day"));
        assertEquals(404, request("/api/chat/extra", "secret", null, "application/json", "/time set day"));
        assertEquals(0, sent);
    }
    @Test void tokensMigratePersistAndDoNotLeak(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve(ConfigLoader.CONFIG_FILE_NAME), "{\"authEnabled\":false,\"token\":\"\"}".getBytes());
        ApiConfig first = ConfigLoader.load(dir), second = ConfigLoader.load(dir);
        assertTrue(first.isAuthEnabled()); assertEquals(first.getToken(), second.getToken());
        assertTrue(first.getToken().length() >= 43);
        assertFalse(first.toString().contains(first.getToken()));
        if (Files.getFileStore(dir).supportsFileAttributeView("posix"))
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(dir.resolve(ConfigLoader.CONFIG_FILE_NAME)));
        assertThrows(IllegalArgumentException.class, () -> ConfigLoader.parse("{\"authEnabled\":true,\"token\":\"\"}"));
    }
    @Test void remoteBindingNeedsExplicitOptIn() {
        start(); server.stop();
        server = new HttpServerManager(new ApiConfig("0.0.0.0", 0, "secret", true), new MinecraftBridge() {
            public boolean isInWorld() { return true; } public String getPlayerName() { return "p"; }
            public ChatResult sendChat(String s) { return ChatResult.ok("sent"); }
        }, "test", "test");
        assertFalse(server.start());
    }
}
