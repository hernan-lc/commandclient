package com.commandapi.functions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single declared function parameter with type, default and validation rules.
 * Immutable.
 */
public final class FunctionParameter {
    private final String name;
    private final String type;
    private final boolean required;
    private final Object defaultValue;
    private final boolean hasDefault;
    private final Double min;
    private final Double max;
    private final List<String> enumValues;
    private final String description;

    public FunctionParameter(String name, String type, boolean required, Object defaultValue,
            boolean hasDefault, Double min, Double max, List<String> enumValues, String description) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid parameter name: " + name);
        }
        if (type == null) {
            throw new IllegalArgumentException("Parameter type is required: " + name);
        }
        String normalized = type.toLowerCase(java.util.Locale.ROOT);
        if (!FunctionTypes.isKnown(normalized)) {
            throw new IllegalArgumentException("Unknown parameter type: " + type);
        }
        this.name = name;
        this.type = normalized;
        this.required = required;
        this.defaultValue = defaultValue;
        this.hasDefault = hasDefault;
        this.min = min;
        this.max = max;
        this.enumValues = enumValues == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(enumValues));
        this.description = description == null ? "" : description;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean hasDefault() {
        return hasDefault;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public String getDescription() {
        return description;
    }
}
