package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/** Spawns a mob. Compiles to a {@code summon} command. */
public final class SummonStep implements FunctionStep {
    private final String entity;
    private final String position;
    private final String nbt;

    public SummonStep(String entity, String position, String nbt) {
        if (entity == null || entity.isEmpty()) {
            throw new IllegalArgumentException("entity is required");
        }
        this.entity = entity;
        this.position = position == null || position.isEmpty() ? "~ ~ ~" : position;
        this.nbt = nbt == null ? "" : nbt;
    }

    public String getEntity() {
        return entity;
    }

    public String getPosition() {
        return position;
    }

    public String getNbt() {
        return nbt;
    }

    @Override
    public String getType() {
        return "summon";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "summon");
        json.addProperty("entity", entity);
        json.addProperty("position", position);
        if (!nbt.isEmpty()) {
            json.addProperty("nbt", nbt);
        }
        return json;
    }
}
