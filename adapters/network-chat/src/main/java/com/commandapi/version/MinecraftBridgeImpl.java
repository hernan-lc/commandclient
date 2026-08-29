package com.commandapi.version;

import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.ClientThreadBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.Executor;

/**
 * Adapter family: network-chat (Minecraft 1.19.3 onwards, including the
 * unobfuscated 26.x releases).
 *
 * <p>1.19.3 removed {@code LocalPlayer.chat} and split sending in two: chat
 * messages go through {@code ClientPacketListener.sendChat}, commands through
 * {@code sendCommand} without the leading slash. Minecraft 26.x ships
 * unobfuscated with these same names, so one source file serves both build
 * families.</p>
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
        ClientPacketListener connection = player.connection;
        if (connection == null) {
            return ChatResult.failure("No server connection");
        }
        if (text.startsWith("/")) {
            connection.sendCommand(text.substring(1));
            return ChatResult.ok("Command sent");
        }
        connection.sendChat(text);
        return ChatResult.ok("Message sent to chat");
    }
}
