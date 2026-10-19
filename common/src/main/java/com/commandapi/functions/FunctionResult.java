package com.commandapi.functions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The terminal outcome of a function run: per-command results and state. */
public final class FunctionResult {
    private final String functionId;
    private final String state;
    private final List<JsonObject> results;
    private final String lastError;
    private final int totalSteps;
    private final int processedSteps;

    public FunctionResult(String functionId, String state, List<JsonObject> results,
            String lastError, int totalSteps, int processedSteps) {
        this.functionId = functionId;
        this.state = state;
        this.results = Collections.unmodifiableList(new ArrayList<JsonObject>(results));
        this.lastError = lastError;
        this.totalSteps = totalSteps;
        this.processedSteps = processedSteps;
    }

    public String getFunctionId() {
        return functionId;
    }

    public String getState() {
        return state;
    }

    public List<JsonObject> getResults() {
        return results;
    }

    public String getLastError() {
        return lastError;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getProcessedSteps() {
        return processedSteps;
    }

    public JsonObject toJson(String jobId) {
        JsonObject json = new JsonObject();
        json.addProperty("id", jobId);
        json.addProperty("function", functionId);
        json.addProperty("state", state);
        json.addProperty("total_steps", totalSteps);
        json.addProperty("processed_steps", processedSteps);
        if (lastError != null) {
            json.addProperty("last_error", lastError);
        }
        JsonArray array = new JsonArray();
        for (JsonObject result : results) {
            array.add(result);
        }
        json.add("results", array);
        return json;
    }
}
