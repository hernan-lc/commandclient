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
        return Minecraft.getInstance().player != null;
    }

    @Override
    public String getPlayerName() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.getName().getString();
    }

    @Override
    protected Executor clientExecutor() {
        return Minecraft.getInstance();
    }

    @Override
    protected ChatResult sendChatOnClientThread(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ChatResult.failure("Player not available (not in world?)");
        }
        player.chat(text);
        return ChatResult.ok(text.startsWith("/") ? "Command sent" : "Message sent to chat");
    }
}
