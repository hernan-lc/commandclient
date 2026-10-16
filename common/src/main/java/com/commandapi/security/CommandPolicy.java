package com.commandapi.security;

import java.util.*;

/** Exact root allowlist. Namespaces are distinct; nested commands require explicit trust. */
public final class CommandPolicy {
    private final Set<String> allowed;
    public CommandPolicy(Collection<String> commands) {
        if (commands == null) throw new IllegalArgumentException("allowedCommands is required");
        Set<String> copy = new HashSet<>();
        for (String name : commands) {
            if (name == null || !name.matches("\\*|[a-z0-9_.:-]+")) throw new IllegalArgumentException("Invalid command policy entry");
            copy.add(name);
        }
        allowed = Collections.unmodifiableSet(copy);
    }
    public boolean allows(String text) {
        if (text == null || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\0') >= 0) return false;
        if (!text.startsWith("/")) return true;
        String command = text.substring(1);
        if (command.isEmpty() || Character.isWhitespace(command.charAt(0))) return false;
        String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return allowed.contains("*") || allowed.contains(root);
    }
    public Set<String> commands() { return allowed; }
}
