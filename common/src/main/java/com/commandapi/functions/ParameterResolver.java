package com.commandapi.functions;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves raw caller arguments against declared {@link FunctionParameter}s
 * with type checking, ranges, enum membership and format validation.
 *
 * <p>Resolved values are returned as validated Java objects (String, Long,
 * Double, Boolean) keyed by parameter name. Rendering to command text happens
 * separately in {@link CommandTemplateRenderer} with context-aware escaping.
 */
public final class ParameterResolver {
    private static final Pattern RESOURCE_LOCATION =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");
    private static final Pattern PLAYER_NAME =
            Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern POSITION =
            Pattern.compile("^[~^]?-?\\d*(\\.\\d+)?( [~^]?-?\\d*(\\.\\d+)?){1,2}$|^~ ~ ~$|^~$");
    private static final Pattern SELECTOR =
            Pattern.compile("^@[apres](\\[[^\\]]*\\])?$|^[A-Za-z0-9_]{1,16}$");

    private ParameterResolver() {
    }

    public static Map<String, Object> resolve(Map<String, FunctionParameter> declared,
            Map<String, Object> supplied) throws FunctionException {
        Map<String, FunctionParameter> params = declared == null
                ? Collections.<String, FunctionParameter>emptyMap() : declared;
        Map<String, Object> args = supplied == null
                ? Collections.<String, Object>emptyMap() : supplied;
        for (String key : args.keySet()) {
            if (!params.containsKey(key)) {
                throw new FunctionException("Unknown parameter: " + key, "UNKNOWN_PARAMETER");
            }
        }
        Map<String, Object> resolved = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, FunctionParameter> entry : params.entrySet()) {
            String name = entry.getKey();
            FunctionParameter param = entry.getValue();
            Object raw = args.containsKey(name) ? args.get(name) : null;
            if (raw == null) {
                if (param.hasDefault()) {
                    raw = param.getDefaultValue();
                } else if (param.isRequired()) {
                    throw new FunctionException("Missing required parameter: " + name,
                            "MISSING_PARAMETER");
                } else {
                    continue;
                }
            }
            resolved.put(name, coerce(name, param, raw));
        }
        return Collections.unmodifiableMap(resolved);
    }

    /** Positional arguments bind to declared parameters in declaration order. */
    public static Map<String, Object> resolvePositional(Map<String, FunctionParameter> declared,
            java.util.List<Object> positional) throws FunctionException {
        Map<String, Object> named = new LinkedHashMap<String, Object>();
        java.util.List<String> names = new java.util.ArrayList<String>(declared.keySet());
        if (positional.size() > names.size()) {
            throw new FunctionException("Too many positional arguments: got " + positional.size()
                    + " for " + names.size() + " parameters", "TOO_MANY_ARGUMENTS");
        }
        for (int i = 0; i < positional.size(); i++) {
            named.put(names.get(i), positional.get(i));
        }
        return resolve(declared, named);
    }

    static Object coerce(String name, FunctionParameter param, Object raw) throws FunctionException {
        String type = param.getType();
        try {
            if ("string".equals(type)) {
                return checkString(name, asString(raw));
            }
            if ("boolean".equals(type)) {
                return asBoolean(name, raw);
            }
            if ("integer".equals(type)) {
                long value = asLong(name, raw);
                checkRange(name, param, value);
                return value;
            }
            if ("number".equals(type)) {
                double value = asDouble(name, raw);
                checkRange(name, param, value);
                return value;
            }
            if ("enum".equals(type)) {
                String value = asString(raw);
                if (!param.getEnumValues().contains(value)) {
                    throw new FunctionException("Invalid value for parameter '" + name
                            + "': expected one of " + param.getEnumValues(), "INVALID_ENUM");
                }
                return value;
            }
            if ("entity".equals(type) || "resource_location".equals(type)) {
                String value = asString(raw);
                if (!RESOURCE_LOCATION.matcher(value.toLowerCase(Locale.ROOT)).matches()) {
                    throw new FunctionException("Invalid resource location for parameter '"
                            + name + "': " + value, "INVALID_RESOURCE");
                }
                return value;
            }
            if ("player".equals(type)) {
                String value = asString(raw);
                if (!PLAYER_NAME.matcher(value).matches()) {
                    throw new FunctionException("Invalid player name for parameter '"
                            + name + "': " + value, "INVALID_PLAYER");
                }
                return value;
            }
            if ("selector".equals(type)) {
                String value = asString(raw);
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                        || !SELECTOR.matcher(value).matches()) {
                    throw new FunctionException("Invalid selector for parameter '"
                            + name + "': " + value, "INVALID_SELECTOR");
                }
                return value;
            }
            if ("position".equals(type)) {
                String value = asString(raw);
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                        || !isPosition(value)) {
                    throw new FunctionException("Invalid position for parameter '"
                            + name + "': " + value, "INVALID_POSITION");
                }
                return value;
            }
        } catch (FunctionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FunctionException("Invalid value for parameter '" + name + "': "
                    + e.getMessage(), "INVALID_VALUE");
        }
        throw new FunctionException("Unknown parameter type: " + type, "UNKNOWN_TYPE");
    }

    private static boolean isPosition(String value) {
        if (value == null || value.isEmpty() || value.length() > 64) {
            return false;
        }
        String[] parts = value.trim().split("\\s+");
        if (parts.length < 1 || parts.length > 3) {
            return false;
        }
        for (String part : parts) {
            if (!part.matches("[~^]?-?\\d*(\\.\\d+)?") && !part.equals("~") && !part.equals("^")) {
                return false;
            }
            if (part.length() > 16) {
                return false;
            }
        }
        return true;
    }

    private static String checkString(String name, String value) throws FunctionException {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\0') >= 0) {
            throw new FunctionException("Invalid value for parameter '" + name
                    + "': must not contain newlines", "INVALID_VALUE");
        }
        if (value.length() > 512) {
            throw new FunctionException("Invalid value for parameter '" + name
                    + "': too long", "VALUE_TOO_LONG");
        }
        return value;
    }

    private static String asString(Object raw) throws FunctionException {
        if (raw instanceof String) {
            return (String) raw;
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return String.valueOf(raw);
        }
        throw new FunctionException("Expected a string, got: "
                + new Gson().toJson(raw), "INVALID_VALUE");
    }

    private static boolean asBoolean(String name, Object raw) throws FunctionException {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String) {
            String text = ((String) raw).toLowerCase(Locale.ROOT).trim();
            if ("true".equals(text)) {
                return true;
            }
            if ("false".equals(text)) {
                return false;
            }
        }
        throw new FunctionException("Invalid boolean for parameter '" + name + "'",
                "INVALID_VALUE");
    }

    private static long asLong(String name, Object raw) throws FunctionException {
        if (raw instanceof Number) {
            double d = ((Number) raw).doubleValue();
            if (d != Math.floor(d)) {
                throw new FunctionException("Invalid integer for parameter '" + name + "'",
                        "INVALID_VALUE");
            }
            return ((Number) raw).longValue();
        }
        if (raw instanceof String) {
            try {
                return Long.parseLong(((String) raw).trim());
            } catch (NumberFormatException e) {
                throw new FunctionException("Invalid integer for parameter '" + name + "'",
                        "INVALID_VALUE");
            }
        }
        throw new FunctionException("Invalid integer for parameter '" + name + "'",
                "INVALID_VALUE");
    }

    private static double asDouble(String name, Object raw) throws FunctionException {
        if (raw instanceof Number) {
            return ((Number) raw).doubleValue();
        }
        if (raw instanceof String) {
            try {
                return Double.parseDouble(((String) raw).trim());
            } catch (NumberFormatException e) {
                throw new FunctionException("Invalid number for parameter '" + name + "'",
                        "INVALID_VALUE");
            }
        }
        throw new FunctionException("Invalid number for parameter '" + name + "'",
                "INVALID_VALUE");
    }

    private static void checkRange(String name, FunctionParameter param, double value)
            throws FunctionException {
        if (param.getMin() != null && value < param.getMin()) {
            throw new FunctionException("Parameter '" + name + "' below minimum "
                    + param.getMin(), "OUT_OF_RANGE");
        }
        if (param.getMax() != null && value > param.getMax()) {
            throw new FunctionException("Parameter '" + name + "' above maximum "
                    + param.getMax(), "OUT_OF_RANGE");
        }
    }
}
