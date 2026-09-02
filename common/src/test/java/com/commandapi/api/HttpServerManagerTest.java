package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests over a real socket. The bridge is faked, so the whole HTTP
 * layer is exercised without Minecraft.
 */
class HttpServerManagerTest {

    private static final Gson GSON = new Gson();

    private HttpServerManager server;

    /** Records what was sent and can pretend the player is missing. */
    private static final class FakeBridge implements MinecraftBridge {
        final List<String> sent = new ArrayList<>();
        volatile boolean inWorld = true;

        @Override
        public boolean isInWorld() {
            return inWorld;
        }

        @Override
        public String getPlayerName() {
            return inWorld ? "Steve" : null;
        }

        @Override
        public ChatResult sendChat(String text) {
            sent.add(text);
            return ChatResult.ok("Message sent to chat");
        }
    }

    private FakeBridge bridge;
    private int port;

    private void start(boolean authEnabled, String token) throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        bridge = new FakeBridge();
        server = new HttpServerManager(
                new ApiConfig("127.0.0.1", port, token, authEnabled), bridge, "1.2.0", "test");
        assertTrue(server.start(), "server should bind");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static final class Response {
        final int status;
        final JsonObject body;

        Response(int status, JsonObject body) {
            this.status = status;
            this.body = body;
        }
    }

    private Response call(String path, String method, String body, String auth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setRequestMethod(method);
        if (auth != null) {
            connection.setRequestProperty("Authorization", auth);
        }
        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String text = "";
        if (stream != null) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int len;
            while ((len = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            stream.close();
        }
        return new Response(status, text.isEmpty() ? new JsonObject() : GSON.fromJson(text, JsonObject.class));
    }

    @Test
    void statusReportsRunningState() throws IOException {
        start(false, "");
        Response response = call("/api/status", "GET", null, null);

        assertEquals(200, response.status);
        assertEquals("running", response.body.get("status").getAsString());
        assertTrue(response.body.get("in_world").getAsBoolean());
        assertEquals("Steve", response.body.get("player_name").getAsString());
    }

    @Test
    void chatSendsTheMessage() throws IOException {
        start(false, "");
        Response response = call("/api/chat", "POST", "{\"text\":\"hello\"}", null);

        assertEquals(200, response.status);
        assertTrue(response.body.get("success").getAsBoolean());
        assertEquals(Collections.singletonList("hello"), bridge.sent);
    }

    @Test
    void executeIsAnAliasOfChat() throws IOException {
        start(false, "");
        assertEquals(200, call("/api/execute", "POST", "{\"command\":\"/seed\"}", null).status);
        assertEquals(Collections.singletonList("/seed"), bridge.sent);
    }

    @Test
    void unknownEndpointReturnsJsonNotFound() throws IOException {
        start(false, "");
        Response response = call("/nope", "GET", null, null);

        assertEquals(404, response.status);
        assertTrue(response.body.get("error").getAsString().contains("Unknown endpoint"));
    }

    @Test
    void wrongMethodIsRejected() throws IOException {
        start(false, "");
        assertEquals(405, call("/api/chat", "GET", null, null).status);
        assertEquals(405, call("/api/status", "POST", "{}", null).status);
    }

    @Test
    void invalidBodyIsRejected() throws IOException {
        start(false, "");
        assertEquals(400, call("/api/chat", "POST", "{not json", null).status);
        assertEquals(400, call("/api/chat", "POST", "{\"nothing\":1}", null).status);
        assertTrue(bridge.sent.isEmpty());
    }

    @Test
    void oversizedBodyIsRejected() throws IOException {
        start(false, "");
        StringBuilder body = new StringBuilder("{\"text\":\"");
        for (int i = 0; i < ApiLimits.MAX_BODY_BYTES + 1024; i++) {
            body.append('x');
        }
        body.append("\"}");

        assertEquals(413, call("/api/chat", "POST", body.toString(), null).status);
        assertTrue(bridge.sent.isEmpty());
    }

    @Test
    void missingPlayerReturnsServiceUnavailable() throws IOException {
        start(false, "");
        bridge.inWorld = false;

        Response response = call("/api/chat", "POST", "{\"text\":\"hello\"}", null);

        assertEquals(503, response.status);
        assertFalse(response.body.get("success").getAsBoolean());
        assertFalse(response.body.getAsJsonObject("result").get("success").getAsBoolean());
        assertTrue(bridge.sent.isEmpty());
    }

    @Test
    void authenticationIsEnforcedOnEveryEndpoint() throws IOException {
        start(true, "secret");

        assertEquals(401, call("/api/status", "GET", null, null).status);
        assertEquals(401, call("/api/chat", "POST", "{\"text\":\"hi\"}", "Bearer wrong").status);
        assertEquals(200, call("/api/status", "GET", null, "Bearer secret").status);
        assertEquals(200, call("/api/chat", "POST", "{\"text\":\"hi\"}", "Bearer secret").status);
        assertEquals(Collections.singletonList("hi"), bridge.sent);
    }

    @Test
    void ephemeralPortResolvesToARealPort() throws IOException {
        bridge = new FakeBridge();
        server = new HttpServerManager(
                new ApiConfig("127.0.0.1", 0, "", false), bridge, "1.2.0", "test");
        assertTrue(server.start(), "server should bind an ephemeral port");
        assertTrue(server.getPort() > 0);
        port = server.getPort();

        Response response = call("/api/status", "GET", null, null);
        assertEquals(200, response.status);
        assertEquals(server.getPort(), response.body.get("port").getAsInt());
        assertTrue(response.body.get("url").getAsString().endsWith(":" + server.getPort()));
    }

    @Test
    void stopReleasesThePortAndIsIdempotent() throws IOException {
        start(false, "");
        server.stop();
        server.stop();
        assertFalse(server.isRunning());

        // The port is free again if it can be bound.
        try (ServerSocket rebind = new ServerSocket(port)) {
            assertTrue(rebind.isBound());
        }
    }
}
