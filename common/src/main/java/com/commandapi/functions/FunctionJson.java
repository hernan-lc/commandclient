package com.commandapi.functions;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;

/** JSON views of function definitions for REST responses. */
public final class FunctionJson {
    private static final Gson GSON = new Gson();

    private FunctionJson() {
    }

    /** Compact entry for listings. */
    public static JsonObject summary(FunctionDefinition definition) {
        JsonObject json = new JsonObject();
        json.addProperty("id", definition.getId());
        json.addProperty("name", definition.getName());
        json.addProperty("description", definition.getDescription());
        json.addProperty("format", definition.getFormat().name().toLowerCase(
                java.util.Locale.ROOT));
        json.addProperty("origin", definition.getOrigin());
        json.addProperty("steps", definition.getSteps().size());
        JsonArray params = new JsonArray();
        for (String name : definition.getParameters().keySet()) {
            params.add(name);
        }
        json.add("parameters", params);
        return json;
    }

    /** Full inspection view with parameter details and raw steps. */
    public static JsonObject view(FunctionDefinition definition) {
        JsonObject json = summary(definition);
        JsonObject params = new JsonObject();
        for (Map.Entry<String, FunctionParameter> entry
                : definition.getParameters().entrySet()) {
            FunctionParameter param = entry.getValue();
            JsonObject decl = new JsonObject();
            decl.addProperty("type", param.getType());
            decl.addProperty("required", param.isRequired());
            if (param.hasDefault()) {
                decl.add("default", GSON.toJsonTree(param.getDefaultValue()));
            }
            if (param.getMin() != null) {
                decl.addProperty("min", param.getMin());
            }
            if (param.getMax() != null) {
                decl.addProperty("max", param.getMax());
            }
            if (!param.getEnumValues().isEmpty()) {
                JsonArray values = new JsonArray();
                for (String value : param.getEnumValues()) {
                    values.add(value);
                }
                decl.add("values", values);
            }
            if (!param.getDescription().isEmpty()) {
                decl.addProperty("description", param.getDescription());
            }
            params.add(entry.getKey(), decl);
        }
        json.add("parameterDetails", params);
        JsonArray steps = new JsonArray();
        for (com.commandapi.functions.steps.FunctionStep step : definition.getSteps()) {
            steps.add(step.toJson());
        }
        json.add("stepDetails", steps);
        return json;
    }
}
