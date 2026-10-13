package com.commandapi.version.mixin;
import com.commandapi.version.NativeCommands;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;
/** Runs native work on the integrated-server thread, including paused-world ticks. */
@Mixin(IntegratedServer.class)
public abstract class CommandApiServerTickMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void commandapiTick(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        NativeCommands.drain((MinecraftServer) (Object) this);
    }
}
