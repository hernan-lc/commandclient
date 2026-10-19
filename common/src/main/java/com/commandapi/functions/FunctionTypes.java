package com.commandapi.functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Known parameter type names. */
public final class FunctionTypes {
    private static final Set<String> KNOWN = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "string", "integer", "number", "boolean", "entity",
            "player", "selector", "position", "enum", "resource_location")));

    private FunctionTypes() {
    }

    public static boolean isKnown(String type) {
        return KNOWN.contains(type);
    }

    public static Set<String> known() {
        return KNOWN;
    }
}
