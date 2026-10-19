package com.commandapi.functions;

import com.commandapi.functions.steps.CommandStep;
import com.commandapi.functions.steps.FunctionStep;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses extended {@code .mcfunction} files: plain command lines, {@code #}
 * comments, and {@code # @param <name> <type> [default...]} headers.
 *
 * <p>A parameterized {@code .mcfunction} is a CommandClient extension, not
 * standard vanilla behavior.</p>
 */
public final class McFunctionParser {
    private static final Pattern PARAM_HEADER =
            Pattern.compile("^#\\s*@param\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_]+)(?:\\s+(.*))?$");
    private static final Pattern ID_HEADER =
            Pattern.compile("^#\\s*([a-z0-9_.-]+:[a-z0-9_/.-]+)\\s*$");

    private McFunctionParser() {
    }

    public static FunctionDefinition parse(String fallbackId, String text, String origin)
            throws FunctionException {
        if (text == null) {
            throw new FunctionException("Empty .mcfunction file", "EMPTY_FILE");
        }
        Map<String, FunctionParameter> params = new LinkedHashMap<String, FunctionParameter>();
        List<FunctionStep> steps = new ArrayList<FunctionStep>();
        String declaredId = null;
        String[] lines = text.split("\n", -1);
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                Matcher param = PARAM_HEADER.matcher(line);
                if (param.matches()) {
                    addParam(params, param.group(1), param.group(2), param.group(3),
                            lineNumber + 1);
                    continue;
                }
                Matcher id = ID_HEADER.matcher(line);
                if (id.matches() && declaredId == null && steps.isEmpty() && params.isEmpty()) {
                    declaredId = id.group(1);
                    continue;
                }
                // Any other comment is documentation, never a command.
                continue;
            }
            if (line.length() > 512) {
                throw new FunctionException("Line " + (lineNumber + 1) + " too long",
                        "COMMAND_TOO_LONG");
            }
            steps.add(new CommandStep(line));
        }
        if (steps.isEmpty()) {
            throw new FunctionException("No commands in .mcfunction file", "EMPTY_FUNCTION");
        }
        String id = declaredId != null ? declaredId : fallbackId;
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new FunctionException("Cannot determine function id for .mcfunction file",
                    "INVALID_ID");
        }
        return new FunctionDefinition(id, shortName(id), "",
                params, steps, FunctionDefinition.Format.MCFUNCTION,
                origin == null ? "" : origin);
    }

    private static void addParam(Map<String, FunctionParameter> params, String name,
            String type, String defaultText, int line) throws FunctionException {
        if (params.containsKey(name)) {
            throw new FunctionException("Duplicate @param '" + name + "' at line " + line,
                    "DUPLICATE_PARAMETER");
        }
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        if (!FunctionTypes.isKnown(normalized)) {
            throw new FunctionException("Unknown @param type '" + type + "' at line " + line,
                    "UNKNOWN_TYPE");
        }
        try {
            boolean hasDefault = defaultText != null && !defaultText.trim().isEmpty();
            Object defaultValue = null;
            if (hasDefault) {
                defaultValue = parseDefault(normalized, defaultText.trim());
            }
            // Validate the default eagerly through the same coercion path.
            FunctionParameter probe = new FunctionParameter(name, normalized, !hasDefault,
                    defaultValue, hasDefault, null, null, null, "");
            if (hasDefault) {
                ParameterResolver.coerce(name, probe, defaultValue);
            }
            params.put(name, probe);
        } catch (IllegalArgumentException e) {
            throw new FunctionException("Invalid @param at line " + line + ": " + e.getMessage(),
                    "INVALID_PARAMETER");
        }
    }

    private static Object parseDefault(String type, String text) {
        // Strip one layer of surrounding quotes for string-ish defaults.
        String unquoted = text;
        if (unquoted.length() >= 2 && unquoted.startsWith("\"") && unquoted.endsWith("\"")) {
            unquoted = unquoted.substring(1, unquoted.length() - 1);
        }
        if ("boolean".equals(type)) {
            return unquoted;
        }
        if ("integer".equals(type)) {
            try {
                return Long.parseLong(unquoted.trim());
            } catch (NumberFormatException e) {
                return unquoted;
            }
        }
        if ("number".equals(type)) {
            try {
                return Double.parseDouble(unquoted.trim());
            } catch (NumberFormatException e) {
                return unquoted;
            }
        }
        return unquoted;
    }

    private static String shortName(String id) {
        int slash = id.indexOf(':');
        return slash >= 0 ? id.substring(slash + 1) : id;
    }
}
