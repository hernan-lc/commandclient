package com.commandapi.functions;

import com.google.gson.Gson;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Secure {@code ${parameter}} interpolation.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Missing parameters are never silently replaced; they raise.</li>
 *   <li>{@code $${name}} renders a literal {@code ${name}}.</li>
 *   <li>Values are validated per declared type before rendering.</li>
 *   <li>Rendered fragments must not contain newlines, section signs or
 *   command separators that would split or chain commands.</li>
 *   <li>JSON text contexts must use {@link #escapeJsonText} /
 *   {@link #quoteJsonText} (Gson serialization), never raw concatenation.</li>
 * </ul>
 */
public final class CommandTemplateRenderer {
    private static final Gson GSON = new Gson();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final String ESCAPED_TOKEN = "\0ESCAPED-DOLLAR\0";

    private CommandTemplateRenderer() {
    }

    /**
     * Renders a template against resolved (already type-validated) values plus
     * declared parameter metadata for context checks.
     */
    public static String render(String template, Map<String, Object> values,
            Map<String, FunctionParameter> declared) throws FunctionException {
        if (template == null) {
            return "";
        }
        String guarded = template.replace("$${", ESCAPED_TOKEN);
        Matcher matcher = PLACEHOLDER.matcher(guarded);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!values.containsKey(name)) {
                if (declared != null && !declared.containsKey(name)) {
                    throw new FunctionException("Unknown parameter: " + name,
                            "UNKNOWN_PARAMETER");
                }
                throw new FunctionException("Missing value for parameter: " + name,
                        "MISSING_PARAMETER");
            }
            String rendered = renderValue(name,
                    declared == null ? null : declared.get(name), values.get(name));
            matcher.appendReplacement(out, Matcher.quoteReplacement(rendered));
        }
        matcher.appendTail(out);
        return out.toString().replace(ESCAPED_TOKEN, "${");
    }

    /** Renders with no declared-type context (raw strings still safety-checked). */
    public static String render(String template, Map<String, Object> values)
            throws FunctionException {
        return render(template, values, null);
    }

    private static String renderValue(String name, FunctionParameter param, Object value)
            throws FunctionException {
        String text = plainText(value);
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0) {
            throw new FunctionException("Parameter '" + name + "' must not contain newlines",
                    "UNSAFE_VALUE");
        }
        // Command separators would chain extra commands: only trusted raw
        // command templates may contain them; interpolated values never may.
        // Selectors/targets keep a narrow grammar from ParameterResolver; other
        // free strings are also barred from chaining here and validated again
        // by the command allowlist after full expansion.
        if (text.contains(";") || text.contains("\u00a7")) {
            throw new FunctionException("Parameter '" + name + "' contains a forbidden character",
                    "UNSAFE_VALUE");
        }
        if (param != null) {
            String type = param.getType().toLowerCase(Locale.ROOT);
            if ("selector".equals(type) || "player".equals(type) || "entity".equals(type)
                    || "resource_location".equals(type) || "position".equals(type)) {
                // Already grammar-validated; additionally bar chaining/quoting abuse.
                if (text.contains("\"") || text.contains("'") || text.contains("`")
                        || text.contains("$(") || text.contains("&&") || text.contains("||")) {
                    throw new FunctionException("Parameter '" + name
                            + "' contains unsafe characters", "UNSAFE_VALUE");
                }
            }
        } else if (text.contains("\"") || text.contains("`") || text.contains("$(")) {
            // Ambiguous raw string interpolation without a declared type:
            // reject rather than guess.
            throw new FunctionException("Untyped interpolation of '" + name
                    + "' contains unsafe characters; declare the parameter type",
                    "UNSAFE_VALUE");
        }
        return text;
    }

    private static String plainText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double) {
            double d = (Double) value;
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
        }
        if (value instanceof Float) {
            float f = (Float) value;
            if (f == Math.floor(f)) {
                return String.valueOf((long) f);
            }
        }
        return String.valueOf(value);
    }

    /** JSON-escapes free text for embedding inside a text component. */
    public static String escapeJsonText(String text) {
        if (text == null) {
            return "";
        }
        String json = GSON.toJson(text);
        return json.substring(1, json.length() - 1);
    }

    /** Serializes free text as a JSON string literal (with quotes). */
    public static String quoteJsonText(String text) {
        return GSON.toJson(text == null ? "" : text);
    }

    /** True when the template references only known parameters. */
    public static void checkReferences(String template, Map<String, FunctionParameter> declared,
            String where) throws FunctionException {
        if (template == null || declared == null) {
            return;
        }
        String guarded = template.replace("$${", "");
        Matcher matcher = PLACEHOLDER.matcher(guarded);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!declared.containsKey(name)) {
                throw new FunctionException("Unknown parameter ${" + name + "} in " + where,
                        "UNKNOWN_PARAMETER");
            }
        }
    }
}
