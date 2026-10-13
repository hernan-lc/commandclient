package com.commandapi.version.mixin;
import com.commandapi.version.NativeCommands;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Publishes connection changes without reading client state from server threads. */
@Mixin(Minecraft.class)
public abstract class CommandApiClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void commandapiTick(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        NativeCommands.observeSession(client.player == null ? null : client.player.connection, client.getSingleplayerServer());
    }
}
