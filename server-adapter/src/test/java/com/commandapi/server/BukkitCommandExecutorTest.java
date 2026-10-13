package com.commandapi.server;
import com.commandapi.minecraft.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BukkitCommandExecutorTest {
    YamlConfiguration config() {
        YamlConfiguration c = new YamlConfiguration(); c.set("token", "secret"); c.set("allowed-commands", Arrays.asList("time")); return c;
    }
    @Test void configurationRejectsConsoleAndInvalidIdentity() {
        YamlConfiguration c = config(); c.set("sender", "console");
        assertThrows(IllegalArgumentException.class, () -> new ServerSettings(c));
        c.set("console-enabled", true); assertTrue(new ServerSettings(c).console);
        c.set("sender", "player"); c.set("http-enabled", true);
        assertThrows(IllegalArgumentException.class, () -> new ServerSettings(c));
        c.set("player-uuid", "not-a-uuid"); assertThrows(IllegalArgumentException.class, () -> new ServerSettings(c));
    }
    @Test void playerPermissionsAndPreprocessCancellationAreRespected() {
        Plugin plugin = mock(Plugin.class); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Player player = mock(Player.class); when(player.getServer()).thenReturn(mock(Server.class)); UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true);
        PluginManager manager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            BukkitCommandExecutor executor = new BukkitCommandExecutor(plugin, new ServerSettings(config()));
            java.util.concurrent.CompletableFuture<ChatResult> denied = executor.executePlayer(player, "/time set day", new CancellationToken()); executor.tick();
            assertEquals("PLAYER_DENIED", denied.join().getCode());
            when(player.hasPermission("commandapi.execute")).thenReturn(true);
            doAnswer(call -> { ((PlayerCommandPreprocessEvent)call.getArgument(0)).setCancelled(true); return null; }).when(manager).callEvent(any());
            java.util.concurrent.CompletableFuture<ChatResult> cancelled = executor.executePlayer(player, "/time set day", new CancellationToken()); executor.tick();
            assertEquals("COMMAND_CANCELLED", cancelled.join().getCode());
            bukkit.verify(() -> Bukkit.dispatchCommand(any(), anyString()), never()); executor.close();
        }
    }
    @Test void dispatchNeverChangesPlayerAuthorityAndDisableResolvesPendingWork() {
        Plugin plugin = mock(Plugin.class); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Player player = mock(Player.class); when(player.getServer()).thenReturn(mock(Server.class)); UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id); when(player.isOnline()).thenReturn(true); when(player.hasPermission("commandapi.execute")).thenReturn(true);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player); bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
            bukkit.when(() -> Bukkit.dispatchCommand(player, "time set day")).thenReturn(true);
            BukkitCommandExecutor executor = new BukkitCommandExecutor(plugin, new ServerSettings(config()));
            java.util.concurrent.CompletableFuture<ChatResult> result = executor.executePlayer(player, "/time set day", new CancellationToken()); executor.tick();
            assertEquals("dispatched", result.join().getStage()); verify(player, never()).setOp(anyBoolean());
            java.util.concurrent.CompletableFuture<ChatResult> pending = executor.executePlayer(player, "/time set day", new CancellationToken()); executor.close();
            assertEquals("SESSION_CHANGED", pending.join().getCode());
        }
    }
}
