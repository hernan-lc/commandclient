package com.commandapi.version.mixin;

import com.commandapi.version.CommandApiCommands;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code /commandapi ...} on the signed-chat generation
 * (1.19 through 1.19.2), where slash input leaves through
 * {@code LocalPlayer.commandSigned} with the slash already stripped.
 */
@Mixin(LocalPlayer.class)
public abstract class CommandApiSignedChatMixin {

    @Inject(method = "commandSigned", at = @At("HEAD"), cancellable = true)
    private void commandapi$onCommandSigned(String command, Component preview, CallbackInfo ci) {
        if (CommandApiCommands.dispatchCommand(this, command)) {
            ci.cancel();
        }
    }
}
