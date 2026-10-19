package com.commandapi.functions.steps;

import com.google.gson.JsonObject;

/** Marker for a parsed function step. Implementations are immutable. */
public interface FunctionStep {
    /** Step discriminator, e.g. {@code command}, {@code summon}, {@code repeat}. */
    String getType();

    /** Structured JSON view used by preview responses. */
    JsonObject toJson();
}
