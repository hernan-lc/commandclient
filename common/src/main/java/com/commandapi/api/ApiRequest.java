package com.commandapi.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed {@code /api/chat} request body.
 *
 * <p>Accepted shapes, in precedence order:</p>
 * <pre>
 * {"text": "hello"}
 * {"command": "/time set day"}   (legacy alias for "text")
 * {"messages": ["a", "b"]}
 * </pre>
 */
public final class ApiRequest {

    private static final Gson GSON = new Gson();

    private final List<String> messages;
    private final boolean batch;

    private ApiRequest(List<String> messages, boolean batch) {
        this.messages = Collections.unmodifiableList(messages);
        this.batch = batch;
    }

    /**
     * @throws ApiRequestException when the body is not valid JSON or carries no
     *                             usable field
     */
    public static ApiRequest parse(String body) {
        JsonObject json;
        try {
            JsonElement element = GSON.fromJson(body, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new ApiRequestException("Request body must be a JSON object");
            }
            json = element.getAsJsonObject();
        } catch (ApiRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ApiRequestException("Malformed JSON: " + e.getMessage());
        }

        if (has(json, "text")) {
            return single(json.get("text"));
        }
        if (has(json, "command")) {
            return single(json.get("command"));
        }
        if (has(json, "messages")) {
            JsonElement element = json.get("messages");
            if (!element.isJsonArray()) {
                throw new ApiRequestException("'messages' must be an array of strings");
            }
            JsonArray array = element.getAsJsonArray();
            if (array.size() == 0) {
                throw new ApiRequestException("'messages' must not be empty");
            }
            if (array.size() > ApiLimits.MAX_BATCH_SIZE) {
                throw new ApiRequestException("'messages' must contain at most "
                        + ApiLimits.MAX_BATCH_SIZE + " entries, got " + array.size());
            }
            List<String> parsed = new ArrayList<>(array.size());
            for (JsonElement item : array) {
                parsed.add(text(item, "messages entry"));
            }
            return new ApiRequest(parsed, true);
        }
        throw new ApiRequestException("Missing 'text' or 'messages' field");
    }

    private static ApiRequest single(JsonElement element) {
        return new ApiRequest(Collections.singletonList(text(element, "'text'")), false);
    }

    private static String text(JsonElement element, String what) {
        if (element == null || element.isJsonNull()
                || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new ApiRequestException(what + " must be a string");
        }
        String value = element.getAsString();
        if (value.isEmpty()) {
            throw new ApiRequestException(what + " must not be empty");
        }
        if (value.length() > ApiLimits.MAX_MESSAGE_LENGTH) {
            throw new ApiRequestException(what + " must be at most "
                    + ApiLimits.MAX_MESSAGE_LENGTH + " characters, got " + value.length());
        }
        return value;
    }

    private static boolean has(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull();
    }

    /** The messages to send, in order; never empty. */
    public List<String> getMessages() {
        return messages;
    }

    /** True when the request used the {@code messages} array form. */
    public boolean isBatch() {
        return batch;
    }
}
