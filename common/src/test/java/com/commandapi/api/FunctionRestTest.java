package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.MinecraftBridge;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end function REST coverage over a real socket with a fake bridge. */
class FunctionRestTest {
    private static final Gson GSON = new Gson();
    private static final String TOKEN = "secret";

    static final class FakeBridge implements MinecraftBridge {
        final List<String> sent = new ArrayList<>();
        volatile boolean inWorld = true;

        @Override public boolean isInWorld() {
            return inWorld;
        }

        @Override public String getPlayerName() {
            return inWorld ? "Steve" : null;
        }

        @Override public ChatResult sendChat(String text) {
            sent.add(text);
            return ChatResult.ok("sent");
        }
    }

    private HttpServerManager server;
    private FakeBridge bridge;
    private int port;

    private void seedFunctions(Path configDir) throws Exception {
        Path root = configDir.resolve("commandapi").resolve("functions").resolve("events");
        Files.createDirectories(root);
        Files.write(root.resolve("mob_attack.json"),
                ("{\"schemaVersion\":1,\"name\":\"mob_attack\","
                        + "\"description\":\"Spawn mobs\","
                        + "\"parameters\":{\"mob\":{\"type\":\"entity\","
                        + "\"default\":\"minecraft:zombie\"},"
                        + "\"count\":{\"type\":\"integer\",\"min\":1,\"max\":5,\"default\":1}},"
                        + "\"steps\":[{\"type\":\"repeat\",\"times\":\"${count}\",\"steps\":["
                        + "{\"type\":\"summon\",\"entity\":\"${mob}\","
                        + "\"position\":\"~ ~1 ~\"}]},"
                        + "{\"type\":\"title\",\"target\":\"@a\",\"title\":\"Hi\","
                        + "\"subtitle\":\"\"}]}").getBytes(StandardCharsets.UTF_8));
        Path utils = configDir.resolve("commandapi").resolve("functions").resolve("utilities");
        Files.createDirectories(utils);
        Files.write(utils.resolve("clear_weather.mcfunction"),
                "# utilities:clear_weather\nweather clear\nsay Sunny\n".getBytes(StandardCharsets.UTF_8));
    }

    private void start(Path configDir, boolean authEnabled) throws Exception {
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        bridge = new FakeBridge();
        server = new HttpServerManager(
                new ApiConfig("127.0.0.1", port, "secret", authEnabled), bridge, "1.4.0",
                "1.20.1", configDir);
        assertTrue(server.start());
    }

    @AfterEach void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    static final class Response {
        final int status;
        final JsonObject body;
        final String location;

        Response(int status, JsonObject body, String location) {
            this.status = status;
            this.body = body;
            this.location = location;
        }
    }

    private Response call(String path, String method, String body, String auth) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setRequestMethod(method);
        if (auth != null) {
            connection.setRequestProperty("Authorization", auth);
        }
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String text = "";
        if (stream != null) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int len;
            while ((len = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            stream.close();
        }
        return new Response(status, text.isEmpty() ? new JsonObject()
                : GSON.fromJson(text, JsonObject.class), connection.getHeaderField("Location"));
    }

    @Test void listAndShowFunctions(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, true);
        Response list = call("/api/functions", "GET", null, "Bearer " + TOKEN);
        assertEquals(200, list.status);
        assertEquals(2, list.body.getAsJsonArray("functions").size());
        Response show = call("/api/functions/events%3Amob_attack", "GET", null, "Bearer " + TOKEN);
        assertEquals(200, show.status);
        assertEquals("events:mob_attack", show.body.get("id").getAsString());
        assertTrue(show.body.has("parameterDetails"));
        assertEquals(404, call("/api/functions/nope%3Aunknown", "GET", null, "Bearer " + TOKEN).status);
    }

    @Test void previewResolvesWithoutExecuting(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, true);
        Response preview = call("/api/functions/events%3Amob_attack/preview", "POST",
                "{\"args\":{\"count\":2}}", "Bearer " + TOKEN);
        assertEquals(200, preview.status);
        assertEquals(2 + 2, preview.body.getAsJsonArray("commands").size());
        assertEquals("/summon minecraft:zombie ~ ~1 ~",
                preview.body.getAsJsonArray("commands").get(0).getAsString());
        assertTrue(bridge.sent.isEmpty());
        // Invalid args are rejected with 400 and execute nothing.
        assertEquals(400, call("/api/functions/events%3Amob_attack/preview", "POST",
                "{\"args\":{\"count\":99}}", "Bearer " + TOKEN).status);
        assertTrue(bridge.sent.isEmpty());
    }

    @Test void syncAndAsyncRuns(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, true);
        Response sync = call("/api/functions/utilities%3Aclear_weather/run", "POST",
                "{\"args\":{},\"async\":false}", "Bearer " + TOKEN);
        assertEquals(200, sync.status);
        assertEquals("completed", sync.body.get("state").getAsString());
        assertEquals(2, bridge.sent.size());

        Response async = call("/api/functions/events%3Amob_attack/run", "POST",
                "{\"args\":{\"count\":2},\"async\":true}", "Bearer " + TOKEN);
        assertEquals(202, async.status);
        assertEquals("events:mob_attack", async.body.get("function").getAsString());
        assertTrue(async.body.has("id"));
        assertTrue(async.body.has("state"));
        assertTrue(async.body.has("total_steps"));
        assertTrue(async.body.has("processed_steps"));
        // Function jobs are visible through the existing job endpoint.
        String jobId = async.body.get("id").getAsString();
        Response job = null;
        for (int i = 0; i < 50 && (job == null || !isTerminal(job)); i++) {
            Thread.sleep(100);
            job = call("/api/jobs/" + jobId, "GET", null, "Bearer " + TOKEN);
        }
        assertEquals(200, job.status);
        assertTrue(isTerminal(job));
    }

    private static boolean isTerminal(Response job) {
        String state = job.body.get("state").getAsString();
        return "completed".equals(state) || "failed".equals(state)
                || "cancelled".equals(state);
    }

    @Test void validateReloadAndCrud(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, true);
        Response valid = call("/api/functions/validate", "POST",
                "{\"id\":\"x:y\",\"format\":\"json\",\"source\":\"{\\\"schemaVersion\\\":1,"
                        + "\\\"name\\\":\\\"x:y\\\",\\\"steps\\\":[{\\\"type\\\":\\\"command\\\","
                        + "\\\"value\\\":\\\"say hi\\\"}]}\"}",
                "Bearer " + TOKEN);
        assertEquals(200, valid.status);
        assertTrue(valid.body.get("valid").getAsBoolean());
        Response invalid = call("/api/functions/validate", "POST",
                "{\"id\":\"x:y\",\"format\":\"json\",\"source\":\"{\\\"steps\\\":[]}\"}",
                "Bearer " + TOKEN);
        assertEquals(200, invalid.status);
        assertFalse(invalid.body.get("valid").getAsBoolean());

        // Create via POST, inspect, update via PUT, delete.
        Response created = call("/api/functions", "POST",
                "{\"id\":\"events:created\",\"source\":\"{\\\"schemaVersion\\\":1,"
                        + "\\\"name\\\":\\\"events:created\\\",\\\"steps\\\":[{\\\"type\\\":"
                        + "\\\"command\\\",\\\"value\\\":\\\"say created\\\"}]}\"}",
                "Bearer " + TOKEN);
        assertEquals(201, created.status);
        assertEquals(200, call("/api/functions/events%3Acreated", "GET", null,
                "Bearer " + TOKEN).status);
        Response updated = call("/api/functions/events%3Acreated", "PUT",
                "{\"source\":\"{\\\"schemaVersion\\\":1,\\\"name\\\":\\\"events:created\\\","
                        + "\\\"steps\\\":[{\\\"type\\\":\\\"command\\\",\\\"value\\\":\\\"say v2\\\"}]}\"}",
                "Bearer " + TOKEN);
        assertEquals(200, updated.status);
        assertEquals(200, call("/api/functions/events%3Acreated", "DELETE", null,
                "Bearer " + TOKEN).status);
        assertEquals(404, call("/api/functions/events%3Acreated", "GET", null,
                "Bearer " + TOKEN).status);

        Response reloaded = call("/api/functions/reload", "POST", "{}", "Bearer " + TOKEN);
        assertEquals(200, reloaded.status);
        assertTrue(reloaded.body.get("reloaded").getAsBoolean());
    }

    @Test void unauthorizedCallsAreRejected(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, true);
        assertEquals(401, call("/api/functions", "GET", null, null).status);
        assertEquals(401, call("/api/functions/events%3Amob_attack/run", "POST", "{}",
                "Bearer wrong").status);
        assertEquals(401, call("/api/functions/validate", "POST", "{}", "Bearer wrong").status);
        assertEquals(401, call("/api/functions/reload", "POST", "{}", "Bearer wrong").status);
    }

    @Test void managementRequiresAuthEnabled(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, false);
        // Reads stay open without auth, matching GET rules.
        assertEquals(200, call("/api/functions", "GET", null, null).status);
        // But file-writing management is forbidden when auth is disabled.
        assertEquals(403, call("/api/functions/reload", "POST", "{}", "Bearer " + TOKEN).status);
        assertEquals(403, call("/api/functions", "POST", "{\"id\":\"x:y\",\"source\":\"say hi\"}",
                "Bearer " + TOKEN).status);
    }

    @Test void methodValidationAndLegacyCompatibility(@TempDir Path configDir) throws Exception {
        seedFunctions(configDir);
        start(configDir, false);
        assertEquals(405, call("/api/functions", "DELETE", null, "Bearer " + TOKEN).status);
        assertEquals(405, call("/api/functions/reload", "GET", null, null).status);
        assertEquals(405, call("/api/functions/events%3Amob_attack/run", "GET", null,
                "Bearer " + TOKEN).status);
        assertEquals(404, call("/api/functions/extra/path/deep", "GET", null,
                "Bearer " + TOKEN).status);
        // Existing endpoints keep working.
        assertEquals(200, call("/api/status", "GET", null, null).status);
        assertEquals(200, call("/api/chat", "POST", "{\"text\":\"hello\"}", "Bearer " + TOKEN).status);
        assertTrue(call("/api/status", "GET", null, null).body.has("functions"));
    }
}
