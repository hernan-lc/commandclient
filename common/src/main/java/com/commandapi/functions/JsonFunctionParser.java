package com.commandapi.functions;

import com.commandapi.functions.steps.CommandStep;
import com.commandapi.functions.steps.ConditionalStep;
import com.commandapi.functions.steps.DelayStep;
import com.commandapi.functions.steps.FunctionCallStep;
import com.commandapi.functions.steps.FunctionStep;
import com.commandapi.functions.steps.ParticleStep;
import com.commandapi.functions.steps.RepeatStep;
import com.commandapi.functions.steps.SoundStep;
import com.commandapi.functions.steps.SummonStep;
import com.commandapi.functions.steps.TitleStep;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the structured JSON function format (schemaVersion 1). */
public final class JsonFunctionParser {
    private static final Gson GSON = new Gson();
    private static final int SUPPORTED_SCHEMA = 1;
    private static final Set<String> STEP_TYPES = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("command", "summon", "title", "sound",
                    "particle", "repeat", "delay", "function", "if")));

    private JsonFunctionParser() {
    }

    public static FunctionDefinition parse(String fallbackId, String text, String origin)
            throws FunctionException {
        JsonObject json;
        try {
            JsonElement element = GSON.fromJson(text, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new FunctionException("Function JSON must be an object", "INVALID_JSON");
            }
            json = element.getAsJsonObject();
        } catch (FunctionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FunctionException("Malformed function JSON: " + e.getMessage(),
                    "INVALID_JSON");
        }
        int schema = json.has("schemaVersion") ? intField(json, "schemaVersion", "root") : 1;
        if (schema != SUPPORTED_SCHEMA) {
            throw new FunctionException("Unsupported schemaVersion " + schema
                    + "; expected " + SUPPORTED_SCHEMA, "UNSUPPORTED_SCHEMA");
        }
        String name = json.has("name") ? strField(json, "name", "root") : fallbackId;
        String id = name != null && name.contains(":") ? name
                : (fallbackId != null ? fallbackId : name);
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new FunctionException("Function needs an id like 'namespace:name'",
                    "INVALID_ID");
        }
        String description = json.has("description") ? strField(json, "description", "root") : "";
        Map<String, FunctionParameter> params = parseParameters(json);
        List<FunctionStep> steps = parseSteps(json, "steps");
        if (steps.isEmpty()) {
            throw new FunctionException("Function " + id + " has no steps", "EMPTY_FUNCTION");
        }
        return new FunctionDefinition(id, shortName(id), description, params, steps,
                FunctionDefinition.Format.JSON, origin == null ? "" : origin);
    }

    private static Map<String, FunctionParameter> parseParameters(JsonObject json)
            throws FunctionException {
        Map<String, FunctionParameter> params = new LinkedHashMap<String, FunctionParameter>();
        if (!json.has("parameters")) {
            return params;
        }
        JsonElement element = json.get("parameters");
        if (!element.isJsonObject()) {
            throw new FunctionException("'parameters' must be an object", "INVALID_PARAMETERS");
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new FunctionException("Invalid parameter name: " + name,
                        "INVALID_PARAMETER");
            }
            if (!entry.getValue().isJsonObject()) {
                throw new FunctionException("Parameter '" + name + "' must be an object",
                        "INVALID_PARAMETER");
            }
            JsonObject decl = entry.getValue().getAsJsonObject();
            if (!decl.has("type")) {
                throw new FunctionException("Parameter '" + name + "' needs a type",
                        "INVALID_PARAMETER");
            }
            String type = decl.get("type").getAsString().toLowerCase(java.util.Locale.ROOT);
            if (!FunctionTypes.isKnown(type)) {
                throw new FunctionException("Unknown type '" + type + "' for parameter '"
                        + name + "'", "UNKNOWN_TYPE");
            }
            boolean hasDefault = decl.has("default") && !decl.get("default").isJsonNull();
            Object defaultValue = hasDefault ? gsonValue(decl.get("default")) : null;
            Double min = decl.has("min") ? decl.get("min").getAsDouble() : null;
            Double max = decl.has("max") ? decl.get("max").getAsDouble() : null;
            List<String> enumValues = null;
            if (decl.has("values")) {
                enumValues = new ArrayList<String>();
                for (JsonElement item : decl.getAsJsonArray("values")) {
                    enumValues.add(item.getAsString());
                }
            }
            String description = decl.has("description") ? decl.get("description").getAsString() : "";
            boolean required = decl.has("required") ? decl.get("required").getAsBoolean()
                    : !hasDefault;
            try {
                FunctionParameter param = new FunctionParameter(name, type, required,
                        defaultValue, hasDefault, min, max, enumValues, description);
                if (hasDefault) {
                    ParameterResolver.coerce(name, param, defaultValue);
                }
                params.put(name, param);
            } catch (IllegalArgumentException e) {
                throw new FunctionException("Invalid parameter '" + name + "': "
                        + e.getMessage(), "INVALID_PARAMETER");
            }
        }
        return params;
    }

    static List<FunctionStep> parseSteps(JsonObject json, String field) throws FunctionException {
        if (!json.has(field)) {
            throw new FunctionException("Missing '" + field + "' array", "MISSING_STEPS");
        }
        JsonElement element = json.get(field);
        if (!element.isJsonArray()) {
            throw new FunctionException("'" + field + "' must be an array", "INVALID_STEPS");
        }
        return parseStepList(element.getAsJsonArray(), field);
    }

    static List<FunctionStep> parseStepList(JsonArray array, String where)
            throws FunctionException {
        List<FunctionStep> steps = new ArrayList<FunctionStep>();
        for (int i = 0; i < array.size(); i++) {
            steps.add(parseStep(array.get(i), where + "[" + i + "]"));
        }
        return steps;
    }

    static FunctionStep parseStep(JsonElement element, String where) throws FunctionException {
        if (element == null || !element.isJsonObject()) {
            throw new FunctionException("Step " + where + " must be an object", "INVALID_STEP");
        }
        JsonObject json = element.getAsJsonObject();
        if (!json.has("type")) {
            throw new FunctionException("Step " + where + " needs a type", "INVALID_STEP");
        }
        String type = json.get("type").getAsString();
        if (!STEP_TYPES.contains(type)) {
            throw new FunctionException("Unknown step type '" + type + "' at " + where,
                    "UNKNOWN_STEP");
        }
        try {
            if ("command".equals(type)) {
                return new CommandStep(strField(json, "value", where));
            }
            if ("summon".equals(type)) {
                return new SummonStep(strField(json, "entity", where),
                        optStr(json, "position", "~ ~ ~"),
                        optStr(json, "nbt", ""));
            }
            if ("title".equals(type)) {
                return new TitleStep(optStr(json, "target", "@a"),
                        optStr(json, "title", ""), optStr(json, "subtitle", ""),
                        optStr(json, "fadeInTicks", "10"), optStr(json, "stayTicks", "60"),
                        optStr(json, "fadeOutTicks", "20"));
            }
            if ("sound".equals(type)) {
                return new SoundStep(strField(json, "sound", where),
                        optStr(json, "source", "master"), optStr(json, "target", "@a"),
                        optStr(json, "volume", "1"), optStr(json, "pitch", "1"),
                        optStr(json, "position", "~ ~ ~"));
            }
            if ("particle".equals(type)) {
                return new ParticleStep(strField(json, "particle", where),
                        optStr(json, "position", "~ ~ ~"), optStr(json, "count", "20"));
            }
            if ("repeat".equals(type)) {
                if (!json.has("times")) {
                    throw new FunctionException("Repeat " + where + " needs 'times'",
                            "INVALID_STEP");
                }
                String times = json.get("times").isJsonPrimitive()
                        ? json.get("times").getAsString() : json.get("times").toString();
                if (!json.has("steps")) {
                    throw new FunctionException("Repeat " + where + " needs 'steps'",
                            "INVALID_STEP");
                }
                return new RepeatStep(times, parseStepList(json.getAsJsonArray("steps"),
                        where + ".steps"));
            }
            if ("delay".equals(type)) {
                if (!json.has("ticks")) {
                    throw new FunctionException("Delay " + where + " needs 'ticks'",
                            "INVALID_STEP");
                }
                String ticks = json.get("ticks").isJsonPrimitive()
                        ? json.get("ticks").getAsString() : json.get("ticks").toString();
                return new DelayStep(ticks);
            }
            if ("function".equals(type)) {
                String target = strField(json, "name", where);
                Map<String, String> args = new LinkedHashMap<String, String>();
                if (json.has("args")) {
                    JsonObject obj = json.getAsJsonObject("args");
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        args.put(entry.getKey(), entry.getValue().isJsonPrimitive()
                                ? entry.getValue().getAsString() : entry.getValue().toString());
                    }
                }
                return new FunctionCallStep(target, args);
            }
            // "if"
            if (!json.has("condition")) {
                throw new FunctionException("Condition " + where + " needs 'condition'",
                        "INVALID_CONDITION");
            }
            JsonObject cond = json.getAsJsonObject("condition");
            String parameter = strField(cond, "parameter", where + ".condition");
            String operator = strField(cond, "operator", where + ".condition")
                    .toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Arrays.asList("eq", "ne", "gt", "gte", "lt", "lte")
                    .contains(operator)) {
                throw new FunctionException("Unknown condition operator '" + operator
                        + "' at " + where, "INVALID_CONDITION");
            }
            String value = cond.has("value") ? (cond.get("value").isJsonPrimitive()
                    ? cond.get("value").getAsString() : cond.get("value").toString()) : "";
            if (!json.has("then")) {
                throw new FunctionException("Condition " + where + " needs 'then'",
                        "INVALID_CONDITION");
            }
            List<FunctionStep> thenSteps = parseStepList(json.getAsJsonArray("then"),
                    where + ".then");
            List<FunctionStep> elseSteps = json.has("else")
                    ? parseStepList(json.getAsJsonArray("else"), where + ".else")
                    : new ArrayList<FunctionStep>();
            return new ConditionalStep(parameter, operator, value, thenSteps, elseSteps);
        } catch (FunctionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FunctionException("Invalid step at " + where + ": " + e.getMessage(),
                    "INVALID_STEP");
        }
    }

    private static Object gsonValue(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                String text = element.getAsString();
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException e) {
                    return element.getAsDouble();
                }
            }
            return element.getAsString();
        }
        return element.toString();
    }

    private static String strField(JsonObject json, String field, String where)
            throws FunctionException {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            throw new FunctionException("Missing '" + field + "' at " + where, "MISSING_FIELD");
        }
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new FunctionException("'" + field + "' must be a string at " + where,
                    "INVALID_FIELD");
        }
        return element.getAsString();
    }

    private static String optStr(JsonObject json, String field, String fallback) {
        if (!json.has(field) || json.get(field).isJsonNull()) {
            return fallback;
        }
        JsonElement element = json.get(field);
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    private static int intField(JsonObject json, String field, String where)
            throws FunctionException {
        try {
            return json.get(field).getAsInt();
        } catch (RuntimeException e) {
            throw new FunctionException("'" + field + "' must be an integer at " + where,
                    "INVALID_FIELD");
        }
    }

    private static String shortName(String id) {
        int slash = id.indexOf(':');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }
}
