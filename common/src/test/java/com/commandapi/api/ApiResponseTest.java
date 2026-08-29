package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiResponseTest {

    @Test
    void buildsErrorDocument() {
        JsonObject error = ApiResponse.error(401, "Unauthorized");
        assertEquals("Unauthorized", error.get("error").getAsString());
        assertEquals(401, error.get("status").getAsInt());
    }

    @Test
    void singleSendUsesResultObject() {
        JsonObject entry = ApiResponse.chatResult("hi", ChatResult.ok("Message sent to chat"));
        JsonObject response = ApiResponse.chat(Collections.singletonList(entry), false);

        assertTrue(response.get("success").getAsBoolean());
        assertFalse(response.has("results"));
        JsonObject result = response.getAsJsonObject("result");
        assertEquals("hi", result.get("text").getAsString());
        assertTrue(result.get("success").getAsBoolean());
        assertEquals("Message sent to chat", result.get("output").getAsString());
    }

    @Test
    void batchSendUsesResultsArray() {
        JsonObject response = ApiResponse.chat(Arrays.asList(
                ApiResponse.chatResult("a", ChatResult.ok("sent")),
                ApiResponse.chatResult("b", ChatResult.failure("Player not available (not in world?)"))), true);

        assertTrue(response.get("success").getAsBoolean());
        assertFalse(response.has("result"));
        assertEquals(2, response.getAsJsonArray("results").size());
        assertFalse(response.getAsJsonArray("results").get(1).getAsJsonObject().get("success").getAsBoolean());
    }

    @Test
    void statusReportsPlayerAndEndpoints() {
        ApiConfig config = new ApiConfig("127.0.0.1", 8080, "", false);
        JsonObject status = ApiResponse.status(config, "127.0.0.1", 8080, true, "Steve", "1.1.0", "1.16.1");

        assertEquals("running", status.get("status").getAsString());
        assertEquals("client-chat", status.get("mode").getAsString());
        assertEquals("http://127.0.0.1:8080", status.get("url").getAsString());
        assertEquals("1.16.1", status.get("minecraft_version").getAsString());
        assertTrue(status.get("in_world").getAsBoolean());
        assertEquals("Steve", status.get("player_name").getAsString());
        assertTrue(status.getAsJsonObject("endpoints").has("/api/execute"));
    }

    @Test
    void statusOmitsPlayerNameWhenNotInWorld() {
        JsonObject status = ApiResponse.status(ApiConfig.defaults(), "127.0.0.1", 8080,
                false, null, "1.1.0", "1.19.4");
        assertFalse(status.get("in_world").getAsBoolean());
        assertFalse(status.has("player_name"));
    }
}
