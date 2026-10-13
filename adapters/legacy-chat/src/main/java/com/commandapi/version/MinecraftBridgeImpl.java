package com.commandapi.version;

import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.ClientThreadBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.Executor;

/**
 * Adapter family: legacy-chat (Minecraft 1.16.x through 1.19.2).
 *
 * <p>These versions send both chat and commands through
 * {@code LocalPlayer.chat}, which accepts a leading {@code /} for commands.
 * That method was removed in 1.19.3, which is where the network-chat family
 * takes over.</p>
 */
public final class MinecraftBridgeImpl extends ClientThreadBridge {

    @Override
    public boolean isInWorld() {
        return onClientThread(() -> Minecraft.getInstance().player != null, false, error -> false);
    }

    @Override
    public String executionMode() {
        return onClientThread(() -> Minecraft.getInstance().isLocalServer() ? "singleplayer-native" : "client-chat",
                "unavailable", error -> "unavailable");
    }

    @Override
    public Object sessionIdentity() {
        return onClientThread(() -> Minecraft.getInstance().player == null ? null
                : Minecraft.getInstance().player.connection, null, error -> null);
    }

    @Override
    public String getPlayerName() {
        return onClientThread(() -> {
            LocalPlayer player = Minecraft.getInstance().player;
            return player == null ? null : player.getName().getString();
        }, null, error -> null);
    }

    @Override
    protected java.util.concurrent.CompletableFuture<ChatResult> nativeCommand(String text) {
        return NativeCommands.submit(text, elevatedSingleplayer());
    }

    @Override
    protected Executor clientExecutor() {
        return Minecraft.getInstance();
    }

    @Override
    protected boolean isClientThread() {
        return Minecraft.getInstance().isSameThread();
    }

    @Override
    protected ChatResult sendChatOnClientThread(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ChatResult.failure("Player not available (not in world?)");
        }
        player.chat(text);
        return ChatResult.ok(text.startsWith("/") ? "Command submitted" : "Message sent to chat");
    }
}
