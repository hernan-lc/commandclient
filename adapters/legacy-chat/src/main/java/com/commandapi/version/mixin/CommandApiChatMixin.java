package com.commandapi.version.mixin;

import com.commandapi.version.CommandApiCommands;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts {@code /commandapi ...} typed in chat on the legacy-chat
 * generation (1.16.x through 1.18.2), where chat and commands both leave
 * through {@code LocalPlayer.chat} with the slash intact.
 */
@Mixin(LocalPlayer.class)
public abstract class CommandApiChatMixin {

    @Inject(method = "chat", at = @At("HEAD"), cancellable = true)
    private void commandapi$onChat(String message, CallbackInfo ci) {
        if (CommandApiCommands.dispatchChat(this, message)) {
            ci.cancel();
        }
    }
}
