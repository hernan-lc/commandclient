package com.commandapi.server;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.*;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.*;
import static org.mockito.Mockito.*;
class PluginLifecycleTest {
    @Test void enableDisableAndEnableAgainReleaseEveryResource(@TempDir Path dir) throws Exception {
        CommandApiPlugin plugin = mock(CommandApiPlugin.class);
        doCallRealMethod().when(plugin).onEnable(); doCallRealMethod().when(plugin).onDisable();
        YamlConfiguration config = new YamlConfiguration(); config.set("token", "test-token");
        Files.write(dir.resolve("config.yml"), config.saveToString().getBytes());
        when(plugin.getConfig()).thenReturn(config); when(plugin.getDataFolder()).thenReturn(dir.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Server server = mock(Server.class); Messenger messenger = mock(Messenger.class); PluginManager manager = mock(PluginManager.class);
        when(server.getMessenger()).thenReturn(messenger); when(server.getPluginManager()).thenReturn(manager); when(plugin.getServer()).thenReturn(server);
        BukkitScheduler scheduler = mock(BukkitScheduler.class); BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<org.bukkit.event.HandlerList> handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler); bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            plugin.onEnable(); plugin.onDisable(); plugin.onEnable(); plugin.onDisable(); plugin.onDisable();
            verify(task, times(2)).cancel();
            verify(messenger, times(2)).registerOutgoingPluginChannel(eq(plugin), eq("commandapi:bridge"));
            verify(messenger, times(3)).unregisterIncomingPluginChannel(plugin);
            verify(manager, never()).disablePlugin(any());
        }
    }
    @Test void invalidConsoleConfigurationDisablesPluginWithoutStartingTransport(@TempDir Path dir) throws Exception {
        CommandApiPlugin plugin = mock(CommandApiPlugin.class); doCallRealMethod().when(plugin).onEnable(); doCallRealMethod().when(plugin).onDisable();
        YamlConfiguration config = new YamlConfiguration(); config.set("token", "test-token"); config.set("sender", "console");
        Files.write(dir.resolve("config.yml"), config.saveToString().getBytes());
        when(plugin.getConfig()).thenReturn(config); when(plugin.getDataFolder()).thenReturn(dir.toFile()); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Server server = mock(Server.class); when(server.getMessenger()).thenReturn(mock(Messenger.class)); when(plugin.getServer()).thenReturn(server);
        PluginManager manager = mock(PluginManager.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class); MockedStatic<org.bukkit.event.HandlerList> handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager); plugin.onEnable(); verify(manager).disablePlugin(plugin);
            verify(server.getMessenger(), never()).registerOutgoingPluginChannel(any(), anyString());
        }
    }
}
