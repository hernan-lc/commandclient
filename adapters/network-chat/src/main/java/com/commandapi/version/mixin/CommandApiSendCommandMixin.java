package com.commandapi.version.mixin;

import com.commandapi.version.CommandApiCommands;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code /commandapi ...} on the network-chat generation
 * (1.19.3 onwards, including unobfuscated 26.x), where slash input leaves
 * through {@code ClientPacketListener.sendCommand} with the slash already
 * stripped.
 */
@Mixin(ClientPacketListener.class)
public abstract class CommandApiSendCommandMixin {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void commandapi$onSendCommand(String command, CallbackInfo ci) {
        if (CommandApiCommands.dispatchCommand(this, command)) {
            ci.cancel();
        }
    }
}
