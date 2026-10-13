package com.commandapi.server;
import com.commandapi.protocol.BridgeProtocol;
import com.commandapi.protocol.BridgeProtocol.*;
import com.commandapi.minecraft.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
class PluginMessageBridgeTest {
    @Test void connectionIdentityAndDuplicateRequestsNeverEscalateOrRedispatch() {
        Plugin plugin = mock(Plugin.class); Player player = mock(Player.class); when(player.isOnline()).thenReturn(true);
        ServerCommandExecutor executor = mock(ServerCommandExecutor.class); when(executor.authorized(player)).thenReturn(true);
        CompletableFuture<ChatResult> future = new CompletableFuture<>(); when(executor.executePlayer(eq(player), anyString(), any())).thenReturn(future);
        PluginMessageBridge bridge = new PluginMessageBridge(plugin, executor);
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, BridgeProtocol.encode(Message.now(Kind.HELLO, UUID.randomUUID(), "")));
        Message execute = Message.now(Kind.EXECUTE, UUID.randomUUID(), "/time set day");
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, BridgeProtocol.encode(execute));
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, BridgeProtocol.encode(execute));
        future.complete(ChatResult.ok("dispatched"));
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, BridgeProtocol.encode(execute));
        verify(executor, times(1)).executePlayer(eq(player), eq("/time set day"), any()); verify(player, never()).setOp(anyBoolean()); bridge.close();
    }
    @Test void unauthorizedAndMalformedFramesCannotExecute() {
        Player player = mock(Player.class); when(player.isOnline()).thenReturn(true); ServerCommandExecutor executor = mock(ServerCommandExecutor.class);
        PluginMessageBridge bridge = new PluginMessageBridge(mock(Plugin.class), executor);
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, new byte[]{1});
        bridge.onPluginMessageReceived(BridgeProtocol.CHANNEL, player, BridgeProtocol.encode(Message.now(Kind.EXECUTE, UUID.randomUUID(), "/stop")));
        verify(executor, never()).executePlayer(any(), anyString(), any()); bridge.close();
    }
}
