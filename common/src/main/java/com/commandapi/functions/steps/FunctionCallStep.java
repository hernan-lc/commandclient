package com.commandapi.functions.steps;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Invokes another registered function by id with named arguments. */
public final class FunctionCallStep implements FunctionStep {
    private final String name;
    private final Map<String, String> args;

    public FunctionCallStep(String name, Map<String, String> args) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("function name is required");
        }
        this.name = name;
        this.args = Collections.unmodifiableMap(new LinkedHashMap<String, String>(
                args == null ? Collections.<String, String>emptyMap() : args));
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    @Override
    public String getType() {
        return "function";
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "function");
        json.addProperty("name", name);
        JsonObject arguments = new JsonObject();
        for (Map.Entry<String, String> entry : args.entrySet()) {
            arguments.addProperty(entry.getKey(), entry.getValue());
        }
        json.add("args", arguments);
        return json;
    }
}
