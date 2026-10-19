package com.commandapi.functions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single thread-safe registry for parsed functions. Both REST and CLI paths
 * access functions through this registry; compilation happens per invocation.
 */
public final class FunctionRegistry {
    private final Map<String, FunctionDefinition> functions = new LinkedHashMap<String, FunctionDefinition>();
    private final Map<String, String> errors = new LinkedHashMap<String, String>();

    public synchronized void register(FunctionDefinition definition) throws FunctionException {
        if (functions.containsKey(definition.getId())) {
            throw new FunctionException("Duplicate function id: " + definition.getId(),
                    "DUPLICATE_FUNCTION");
        }
        functions.put(definition.getId(), definition);
        errors.remove(definition.getId());
    }

    public synchronized void replace(FunctionDefinition definition) {
        functions.put(definition.getId(), definition);
        errors.remove(definition.getId());
    }

    public synchronized FunctionDefinition get(String id) {
        return functions.get(id);
    }

    public synchronized boolean remove(String id) {
        return functions.remove(id) != null;
    }

    public synchronized List<FunctionDefinition> list() {
        return new ArrayList<FunctionDefinition>(functions.values());
    }

    public synchronized void clear() {
        functions.clear();
        errors.clear();
    }

    public synchronized void recordError(String id, String message) {
        errors.put(id, message);
    }

    public synchronized Map<String, String> errors() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(errors));
    }

    public synchronized Collection<String> ids() {
        return new ArrayList<String>(functions.keySet());
    }
}
