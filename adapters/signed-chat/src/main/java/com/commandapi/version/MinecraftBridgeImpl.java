package com.commandapi.version;

import com.commandapi.minecraft.ChatResult;
import com.commandapi.minecraft.ClientThreadBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.concurrent.Executor;

/**
 * Adapter family: signed-chat (Minecraft 1.19 through 1.19.2).
 *
 * <p>1.19 introduced signed chat and replaced {@code LocalPlayer.chat} with
 * {@code chatSigned} / {@code commandSigned}, both of which take a nullable
 * "preview" component. 1.19.3 dropped these again in favour of the connection
 * methods used by the network-chat family, so this generation needs its own
 * adapter.</p>
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
        // null preview: the client has not run chat preview for this message.
        if (text.startsWith("/")) {
            player.commandSigned(text.substring(1), null);
            return ChatResult.ok("Command sent");
        }
        player.chatSigned(text, null);
        return ChatResult.ok("Message sent to chat");
    }
}
