package com.commandapi.server;
import com.commandapi.api.HttpServerManager;
import com.commandapi.config.ApiConfig;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PluginHttpIntegrationTest {
    @Test void realHttpAuthenticatesBeforeConsoleTickDispatch() throws Exception {
        Plugin plugin = mock(Plugin.class); when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        YamlConfiguration config = new YamlConfiguration(); config.set("sender", "console"); config.set("console-enabled", true); config.set("token", "secret"); config.set("allowed-commands", Arrays.asList("time"));
        ServerSettings settings = new ServerSettings(config); org.bukkit.command.ConsoleCommandSender console = mock(org.bukkit.command.ConsoleCommandSender.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true); bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            bukkit.when(Bukkit::getPluginManager).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            bukkit.when(() -> Bukkit.dispatchCommand(console, "time set day")).thenReturn(true);
            // HTTP worker metadata uses the production scheduler; queue native dispatch remains main-thread-only.
            // A console-only bridge wrapper supplies stable metadata without mocking Bukkit on unrelated worker threads.
            BukkitCommandExecutor executor = new BukkitCommandExecutor(plugin, settings);
            com.commandapi.minecraft.MinecraftBridge bridge = new com.commandapi.minecraft.MinecraftBridge() {
                public boolean isInWorld() { return true; } public String getPlayerName() { return "console"; }
                public Object sessionIdentity() { return console; }
                public com.commandapi.minecraft.ChatResult sendChat(String text) { throw new AssertionError(); }
                public CompletableFuture<com.commandapi.minecraft.ChatResult> submitChat(String text, Object session, com.commandapi.minecraft.CancellationToken token) { return executor.submitChat(text, session, token); }
            };
            HttpServerManager http = new HttpServerManager(new ApiConfig("127.0.0.1", 0, "secret", true), bridge, "test", "test"); assertTrue(http.start());
            ExecutorService caller = Executors.newSingleThreadExecutor();
            try {
                Future<Integer> response = caller.submit(() -> {
                    HttpURLConnection c = (HttpURLConnection)new URL("http://127.0.0.1:"+http.getPort()+"/api/chat").openConnection();
                    c.setRequestMethod("POST"); c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Authorization","Bearer secret"); c.setDoOutput(true);
                    c.getOutputStream().write("{\"text\":\"/time set day\"}".getBytes(StandardCharsets.UTF_8)); int code=c.getResponseCode(); c.getInputStream().close(); c.disconnect(); return code;
                });
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while (!response.isDone() && System.nanoTime()<deadline) { executor.tick(); Thread.sleep(5); }
                assertEquals(200,response.get(2,TimeUnit.SECONDS)); bukkit.verify(() -> Bukkit.dispatchCommand(console,"time set day"),times(1));
            } finally { caller.shutdownNow(); http.stop(); executor.close(); }
        }
    }
}
