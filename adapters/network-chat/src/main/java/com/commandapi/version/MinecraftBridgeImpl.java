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
    private final com.commandapi.transport.TransportRegistry transports = new com.commandapi.transport.TransportRegistry(
            new com.commandapi.transport.SingleplayerNativeTransport((text, token) -> NativeCommands.submit(text, elevatedSingleplayer(), token)),
            ClientNetworking.PLUGIN, new com.commandapi.transport.VanillaChatTransport(this::sendChatOnClientThread));

    @Override
    public boolean isInWorld() {
        return onClientThread(() -> Minecraft.getInstance().player != null, false, error -> false);
    }

    @Override protected void configureNative(com.commandapi.config.ApiConfig config) { NativeCommands.configure(config); }

    @Override public com.google.gson.JsonObject capabilities() {
        return onClientThread(() -> {
            com.google.gson.JsonObject json = ClientNetworking.PLUGIN.capabilities();
            json.addProperty("singleplayer_native", true);
            json.addProperty("async_jobs", true);
            json.addProperty("elevated_singleplayer", elevatedSingleplayer());
            json.addProperty("active_transport", transports.selected(Minecraft.getInstance().isLocalServer()));
            return json;
        }, new com.google.gson.JsonObject(), error -> new com.google.gson.JsonObject());
    }

    @Override
    public String executionMode() {
        return onClientThread(() -> transports.selected(Minecraft.getInstance().isLocalServer()),
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
    protected java.util.concurrent.CompletableFuture<ChatResult> nativeCommand(String text, com.commandapi.minecraft.CancellationToken cancellation) {
        ClientNetworking.tick();
        Object session = sessionIdentity();
        return transports.execute(Minecraft.getInstance().isLocalServer(), text, cancellation,
                () -> ClientNetworking.isCurrentSession(session) && java.util.Objects.equals(session, sessionIdentity()));
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
        ClientPacketListener connection = player.connection;
        if (connection == null) {
            return ChatResult.failure("No server connection");
        }
        if (text.startsWith("/")) {
            connection.sendCommand(text.substring(1));
            return ChatResult.ok("Command submitted");
        }
        connection.sendChat(text);
        return ChatResult.ok("Message sent to chat");
    }
}
