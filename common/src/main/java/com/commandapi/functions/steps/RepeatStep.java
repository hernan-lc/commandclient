package com.commandapi.functions.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Repeats nested steps a fixed number of times. */
public final class RepeatStep implements FunctionStep {
    private final String times;
    private final List<FunctionStep> steps;

    public RepeatStep(String times, List<FunctionStep> steps) {
        if (times == null || times.isEmpty()) {
            throw new IllegalArgumentException("repeat times is required");
        }
        this.times = times;
        this.steps = Collections.unmodifiableList(new ArrayList<FunctionStep>(steps));
    }

    public String getTimes() {
        return times;
    }

    public List<FunctionStep> getSteps() {
        return steps;
    }

    @Override
    public String getType() {
        return "repeat";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "repeat");
        json.addProperty("times", times);
        JsonArray array = new JsonArray();
        for (FunctionStep step : steps) {
            array.add(step.toJson());
        }
        json.add("steps", array);
        return json;
    }
}
