package com.commandapi.api;

import com.commandapi.config.ApiConfig;
import com.commandapi.minecraft.ChatResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the JSON documents returned by the API. The wire format matches the
 * pre-refactor mod so existing clients keep working.
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    /** {@code {"error": ..., "status": ...}} */
    public static JsonObject error(int statusCode, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        error.addProperty("status", statusCode);
        return error;
    }

    /** One entry of a chat send: {@code {"text","success","output"}}. */
    public static JsonObject chatResult(String text, ChatResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        json.addProperty("success", result.isSuccess());
        json.addProperty("output", result.getMessage());
        return json;
    }

    /**
     * Single send: {@code {"success":true,"result":{...}}}.
     * Batch: {@code {"success":true,"results":[{...}]}}.
     */
    public static JsonObject chat(List<JsonObject> results, boolean batch) {
        JsonObject response = new JsonObject();
        if (batch) {
            JsonArray array = new JsonArray();
            for (JsonObject result : results) {
                array.add(result);
            }
            response.add("results", array);
        } else {
            response.add("result", results.get(0));
        }
        response.addProperty("success", true);
        return response;
    }

    /**
     * Body for a request that could not be attempted because there is no
     * player. Keeps the shape of a normal chat response (so existing clients
     * can still read {@code result}/{@code results}) while the HTTP status
     * says 503.
     */
    public static JsonObject unavailable(String reason, List<String> messages, boolean batch) {
        List<JsonObject> results = new ArrayList<>();
        for (String message : messages) {
            results.add(chatResult(message, ChatResult.failure(reason)));
        }
        JsonObject response = chat(results, batch);
        response.addProperty("success", false);
        response.addProperty("error", reason);
        return response;
    }

    /** Body of {@code GET /api/status}. */
    public static JsonObject status(ApiConfig config, String host, int port,
                                    boolean inWorld, String playerName, String modVersion,
                                    String minecraftVersion) {
        JsonObject status = new JsonObject();
        status.addProperty("status", "running");
        status.addProperty("mode", "client-chat");
        status.addProperty("mod_version", modVersion);
        status.addProperty("minecraft_version", minecraftVersion);
        status.addProperty("host", host);
        status.addProperty("port", port);
        status.addProperty("url", "http://" + host + ":" + port);
        status.addProperty("auth_enabled", config.isAuthEnabled());
        status.addProperty("in_world", inWorld);
        if (playerName != null) {
            status.addProperty("player_name", playerName);
        }

        JsonObject endpoints = new JsonObject();
        endpoints.addProperty("/api/status", "GET - Check API status");
        endpoints.addProperty("/api/chat", "POST - Send chat message");
        endpoints.addProperty("/api/execute", "POST - Alias for /api/chat");
        status.add("endpoints", endpoints);
        return status;
    }
}
