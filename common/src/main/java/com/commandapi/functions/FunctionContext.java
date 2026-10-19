package com.commandapi.functions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Map;

/** Per-invocation resolved arguments plus function metadata for execution. */
public final class FunctionContext {
    private final String functionId;
    private final Map<String, Object> args;

    public FunctionContext(String functionId, Map<String, Object> args) {
        this.functionId = functionId;
        this.args = args;
    }

    public String getFunctionId() {
        return functionId;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    /** Redacted view for logs: argument values are never logged. */
    public JsonObject toLogJson() {
        JsonObject json = new JsonObject();
        json.addProperty("function", functionId);
        JsonArray names = new JsonArray();
        for (String key : args.keySet()) {
            names.add(key);
        }
        json.add("arg_names", names);
        json.addProperty("arg_count", args.size());
        return json;
    }
}
