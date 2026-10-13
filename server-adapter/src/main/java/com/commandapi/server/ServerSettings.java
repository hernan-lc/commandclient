package com.commandapi.server;
import com.commandapi.config.ApiConfig;
import com.commandapi.security.CommandPolicy;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;
/** Startup validation shared by HTTP and plugin-message transports. */
final class ServerSettings {
    final boolean httpEnabled, console;
    final UUID selectedPlayer;
    final Set<UUID> players = new HashSet<>();
    final CommandPolicy policy;
    final ApiConfig api;
    ServerSettings(FileConfiguration config) {
        httpEnabled = config.getBoolean("http-enabled", false);
        String mode = config.getString("sender", "player");
        if (!mode.equals("player") && !mode.equals("console")) throw new IllegalArgumentException("sender must be player or console");
        console = mode.equals("console");
        if (console && !config.getBoolean("console-enabled", false)) throw new IllegalArgumentException("Console mode requires console-enabled=true");
        String id = config.getString("player-uuid", "");
        selectedPlayer = id.isEmpty() ? null : UUID.fromString(id);
        if (httpEnabled && !console && selectedPlayer == null) throw new IllegalArgumentException("Player HTTP mode requires player-uuid");
        for (String player : config.getStringList("allowed-players")) players.add(UUID.fromString(player));
        policy = new CommandPolicy(config.getStringList("allowed-commands"));
        api = new ApiConfig(config.getString("host", "127.0.0.1"), config.getInt("port", 0), config.getString("token", ""), true, false,
                config.getBoolean("allow-remote-privileged", false), false, policy.commands(), config.getInt("max-jobs", 128),
                config.getInt("max-queued-commands", 1024), config.getInt("commands-per-tick", 8), config.getInt("retention-seconds", 600),
                config.getInt("http-workers", 4), config.getInt("max-http-queue", 32), config.getInt("execution-timeout-seconds", 30), 1);
        if (api.isExposedBeyondLoopback() && !api.isAllowRemotePrivileged()) throw new IllegalArgumentException("Remote HTTP requires allow-remote-privileged=true");
    }
}
