package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/** Waits a bounded number of game ticks between commands. */
public final class DelayStep implements FunctionStep {
    private final String ticks;

    public DelayStep(String ticks) {
        if (ticks == null || ticks.isEmpty()) {
            throw new IllegalArgumentException("delay ticks is required");
        }
        this.ticks = ticks;
    }

    public String getTicks() {
        return ticks;
    }

    @Override
    public String getType() {
        return "delay";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "delay");
        json.addProperty("ticks", ticks);
        return json;
    }
}
