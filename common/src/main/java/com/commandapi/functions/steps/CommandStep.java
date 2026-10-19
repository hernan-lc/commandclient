package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/** A raw Minecraft command line (without or with a leading slash). */
public final class CommandStep implements FunctionStep {
    private final String value;

    public CommandStep(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("command value is required");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String getType() {
        return "command";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "command");
        json.addProperty("value", value);
        return json;
    }
}
