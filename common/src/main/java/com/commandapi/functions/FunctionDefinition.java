package com.commandapi.functions;

import com.commandapi.functions.steps.FunctionStep;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable validated function definition. Steps are the raw parsed step
 * models; compilation to an execution plan happens per invocation with
 * caller-supplied arguments.
 */
public final class FunctionDefinition {
    /** Origin format of the definition. */
    public enum Format {
        MCFUNCTION, JSON
    }

    private final String id;
    private final String name;
    private final String description;
    private final Map<String, FunctionParameter> parameters;
    private final List<FunctionStep> steps;
    private final Format format;
    private final String origin;

    public FunctionDefinition(String id, String name, String description,
            Map<String, FunctionParameter> parameters, List<FunctionStep> steps,
            Format format, String origin) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            throw new IllegalArgumentException("Invalid function id: " + id);
        }
        this.id = id;
        this.name = name == null ? id : name;
        this.description = description == null ? "" : description;
        this.parameters = Collections.unmodifiableMap(
                new LinkedHashMap<String, FunctionParameter>(parameters == null
                        ? Collections.<String, FunctionParameter>emptyMap() : parameters));
        this.steps = Collections.unmodifiableList(
                new ArrayList<FunctionStep>(steps == null
                        ? Collections.<FunctionStep>emptyList() : steps));
        this.format = format == null ? Format.JSON : format;
        this.origin = origin == null ? "" : origin;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, FunctionParameter> getParameters() {
        return parameters;
    }

    public List<FunctionStep> getSteps() {
        return steps;
    }

    public Format getFormat() {
        return format;
    }

    public String getOrigin() {
        return origin;
    }
}
