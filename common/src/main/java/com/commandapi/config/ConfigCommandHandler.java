package com.commandapi.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the arguments of the in-game {@code /commandapi} command and applies
 * them through {@link Actions}. Version independent and free of Minecraft
 * classes: version modules only intercept the chat line, display the returned
 * lines and cancel the send. Every line is a separate chat message, so no
 * embedded newlines are used.
 */
public final class ConfigCommandHandler {

    /** The command as typed in chat, without the leading slash. */
    public static final String COMMAND_NAME = "commandapi";

    /**
     * Side effects the handler needs. Implemented by
     * {@link com.commandapi.CommandApiService}; faked in tests.
     */
    public interface Actions {
        ApiConfig currentConfig();

        boolean isRunning();

        /** The address that was actually bound, e.g. {@code 127.0.0.1:51234}. */
        String runningAddress();

        /**
         * Persists {@code newConfig} and restarts the server on it.
         *
         * @return true when the server is serving the new config afterwards.
         */
        boolean applyAndRestart(ApiConfig newConfig);

        /** Re-reads the config file from disk and restarts on it. */
        boolean reloadFromDisk();
    }

    private final Actions actions;

    public ConfigCommandHandler(Actions actions) {
        if (actions == null) {
            throw new IllegalArgumentException("actions must not be null");
        }
        this.actions = actions;
    }

    /**
     * Handles one command line (the part after {@code /commandapi}, may be
     * empty) and returns one chat line per list entry.
     */
    public List<String> handle(String args) {
        List<String> tokens = split(args);
        if (tokens.isEmpty() || isHelp(tokens.get(0))) {
            return help();
        }
        String sub = tokens.get(0).toLowerCase();
        if ("status".equals(sub)) {
            return status();
        }
        if ("port".equals(sub)) {
            return port(argument(tokens, 1));
        }
        if ("host".equals(sub)) {
            return host(argument(tokens, 1));
        }
        if ("auth".equals(sub)) {
            return auth(argument(tokens, 1));
        }
        if ("token".equals(sub)) {
            return token(argument(tokens, 1));
        }
        if ("reload".equals(sub)) {
            return reload();
        }
        if ("restart".equals(sub)) {
            return restart();
        }
        List<String> lines = new ArrayList<String>();
        lines.add("[CommandAPI] Unknown subcommand \"" + tokens.get(0)
                + "\" - try /commandapi help.");
        return lines;
    }

    private List<String> help() {
        List<String> lines = new ArrayList<String>();
        lines.add("[CommandAPI] /commandapi status - show address and state");
        lines.add("[CommandAPI] /commandapi port <0-65535> - 0 picks a free port");
        lines.add("[CommandAPI] /commandapi host <address> - default 127.0.0.1");
        lines.add("[CommandAPI] /commandapi auth <on|off>");
        lines.add("[CommandAPI] /commandapi token <secret|clear>");
        lines.add("[CommandAPI] /commandapi reload - re-read commandapi.json");
        return lines;
    }

    private List<String> status() {
        ApiConfig config = actions.currentConfig();
        List<String> lines = new ArrayList<String>();
        if (actions.isRunning()) {
            lines.add("[CommandAPI] Running at http://" + actions.runningAddress());
        } else {
            lines.add("[CommandAPI] Server is not running.");
        }
        String portNote = config.isEphemeral()
                ? " (automatic - see the log or commandapi-address.json)"
                : "";
        lines.add("[CommandAPI] Config: host=" + config.getHost()
                + " port=" + describePort(config) + portNote
                + " auth=" + (config.isAuthEnabled() ? "on" : "off")
                + " token=" + (config.getToken().isEmpty() ? "not set" : "set"));
        return lines;
    }

    private List<String> port(String arg) {
        if (arg == null) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Usage: /commandapi port <0-65535> (0 = automatic).");
            lines.add("[CommandAPI] " + currentPortLine());
            return lines;
        }
        int port;
        try {
            port = Integer.parseInt(arg);
        } catch (NumberFormatException e) {
            return single("[CommandAPI] \"" + arg + "\" is not a number - usage: /commandapi port <0-65535>.");
        }
        if (!ApiConfig.isValidPort(port)) {
            return single("[CommandAPI] Port must be 0-65535 - got " + port + ".");
        }
        ApiConfig current = actions.currentConfig();
        return apply(new ApiConfig(current.getHost(), port, current.getToken(), current.isAuthEnabled()),
                port == ApiConfig.DEFAULT_PORT
                        ? "Port set to automatic."
                        : "Port set to " + port + ".");
    }

    private List<String> host(String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Usage: /commandapi host <address> (default 127.0.0.1).");
            lines.add("[CommandAPI] Current host: " + actions.currentConfig().getHost());
            return lines;
        }
        String host = arg.trim();
        if (host.contains(" ") || host.contains("/")) {
            return single("[CommandAPI] \"" + host + "\" is not a valid bind address.");
        }
        ApiConfig current = actions.currentConfig();
        return apply(new ApiConfig(host, current.getPort(), current.getToken(), current.isAuthEnabled()),
                "Host set to " + host + ".");
    }

    private List<String> auth(String arg) {
        if (arg == null) {
            return single("[CommandAPI] Usage: /commandapi auth <on|off>.");
        }
        boolean enabled;
        if ("on".equalsIgnoreCase(arg) || "true".equalsIgnoreCase(arg)) {
            enabled = true;
        } else if ("off".equalsIgnoreCase(arg) || "false".equalsIgnoreCase(arg)) {
            enabled = false;
        } else {
            return single("[CommandAPI] Usage: /commandapi auth <on|off>.");
        }
        ApiConfig current = actions.currentConfig();
        if (enabled && current.getToken().isEmpty()) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Set a token first: /commandapi token <secret>.");
            lines.add("[CommandAPI] Auth stays off until a token is configured.");
            return lines;
        }
        return apply(new ApiConfig(current.getHost(), current.getPort(), current.getToken(), enabled),
                "Authentication " + (enabled ? "enabled." : "disabled."));
    }

    private List<String> token(String arg) {
        if (arg == null) {
            return single("[CommandAPI] Usage: /commandapi token <secret> or /commandapi token clear.");
        }
        ApiConfig current = actions.currentConfig();
        if ("clear".equalsIgnoreCase(arg)) {
            return apply(new ApiConfig(current.getHost(), current.getPort(), "", false),
                    "Token cleared and authentication disabled.");
        }
        if (arg.contains(" ")) {
            return single("[CommandAPI] The token must be a single word without spaces.");
        }
        return apply(new ApiConfig(current.getHost(), current.getPort(), arg, true),
                "Token updated and authentication enabled.");
    }

    private List<String> reload() {
        if (actions.reloadFromDisk()) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Config reloaded.");
            lines.add("[CommandAPI] " + currentPortLine());
            return lines;
        }
        return single("[CommandAPI] Reload failed - the server is not running. Check the log.");
    }

    private List<String> restart() {
        if (actions.applyAndRestart(actions.currentConfig())) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Server restarted.");
            lines.add("[CommandAPI] " + currentPortLine());
            return lines;
        }
        return single("[CommandAPI] Restart failed - the port may be in use. Check the log.");
    }

    private List<String> apply(ApiConfig newConfig, String whatChanged) {
        if (!actions.applyAndRestart(newConfig)) {
            List<String> lines = new ArrayList<String>();
            lines.add("[CommandAPI] Could not bind " + newConfig.getHost() + ":" + newConfig.getPort()
                    + " - keeping the previous config. Check the log.");
            return lines;
        }
        List<String> lines = new ArrayList<String>();
        lines.add("[CommandAPI] " + whatChanged);
        lines.add("[CommandAPI] " + currentPortLine());
        if (newConfig.isExposedBeyondLoopback() && !newConfig.isAuthEnabled()) {
            lines.add("[CommandAPI] WARNING: reachable beyond this machine with auth OFF.");
            lines.add("[CommandAPI] Run /commandapi token <secret> to lock it down.");
        }
        return lines;
    }

    private String currentPortLine() {
        if (actions.isRunning()) {
            return "Serving at http://" + actions.runningAddress() + ".";
        }
        return "The server is not running.";
    }

    private static String describePort(ApiConfig config) {
        if (config.isEphemeral()) {
            return "auto";
        }
        return String.valueOf(config.getPort());
    }

    private static boolean isHelp(String token) {
        return "help".equalsIgnoreCase(token) || "?".equals(token);
    }

    private static String argument(List<String> tokens, int index) {
        if (tokens.size() > index) {
            return tokens.get(index);
        }
        return null;
    }

    private static List<String> single(String line) {
        List<String> lines = new ArrayList<String>();
        lines.add(line);
        return lines;
    }

    private static List<String> split(String args) {
        List<String> tokens = new ArrayList<String>();
        if (args == null) {
            return tokens;
        }
        for (String token : args.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}
