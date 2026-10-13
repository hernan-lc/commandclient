package com.commandapi.server;
import com.commandapi.minecraft.*;
import org.bukkit.entity.Player;
import java.util.concurrent.CompletableFuture;
/** Transport-independent server execution. Player connection supplies identity. */
public interface ServerCommandExecutor extends AutoCloseable {
    CompletableFuture<ChatResult> executePlayer(Player player, String command, CancellationToken cancellation);
    boolean authorized(Player player);
    void tick();
    void close();
}
