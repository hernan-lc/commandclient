package com.commandapi.functions.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic parameter-based conditional over nested steps. */
public final class ConditionalStep implements FunctionStep {
    private final String parameter;
    private final String operator;
    private final String value;
    private final List<FunctionStep> thenSteps;
    private final List<FunctionStep> elseSteps;

    public ConditionalStep(String parameter, String operator, String value,
            List<FunctionStep> thenSteps, List<FunctionStep> elseSteps) {
        if (parameter == null || parameter.isEmpty()) {
            throw new IllegalArgumentException("condition parameter is required");
        }
        if (operator == null || operator.isEmpty()) {
            throw new IllegalArgumentException("condition operator is required");
        }
        this.parameter = parameter;
        this.operator = operator.toLowerCase(java.util.Locale.ROOT);
        this.value = value == null ? "" : value;
        this.thenSteps = Collections.unmodifiableList(new ArrayList<FunctionStep>(thenSteps));
        this.elseSteps = Collections.unmodifiableList(new ArrayList<FunctionStep>(
                elseSteps == null ? Collections.<FunctionStep>emptyList() : elseSteps));
    }

    public String getParameter() {
        return parameter;
    }

    public String getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    public List<FunctionStep> getThenSteps() {
        return thenSteps;
    }

    public List<FunctionStep> getElseSteps() {
        return elseSteps;
    }

    @Override
    public String getType() {
        return "if";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "if");
        JsonObject condition = new JsonObject();
        condition.addProperty("parameter", parameter);
        condition.addProperty("operator", operator);
        condition.addProperty("value", value);
        json.add("condition", condition);
        JsonArray thenArray = new JsonArray();
        for (FunctionStep step : thenSteps) {
            thenArray.add(step.toJson());
        }
        json.add("then", thenArray);
        JsonArray elseArray = new JsonArray();
        for (FunctionStep step : elseSteps) {
            elseArray.add(step.toJson());
        }
        json.add("else", elseArray);
        return json;
    }
}
