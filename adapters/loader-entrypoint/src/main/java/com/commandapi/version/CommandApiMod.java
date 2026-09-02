package com.commandapi.version;

import com.commandapi.CommandApiService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric client entrypoint for this Minecraft version.
 *
 * <p>All it does is hand the shared {@link CommandApiService} a config
 * directory and this version's {@link MinecraftBridgeImpl}; the API itself is
 * version independent.</p>
 *
 * <p>The instance is also published statically so the {@code /commandapi}
 * chat mixins (which cannot receive injections) can reach the service.</p>
 */
public class CommandApiMod implements ClientModInitializer {

    private static CommandApiMod instance;

    private CommandApiService service;

    @Override
    public void onInitializeClient() {
        instance = this;
        service = new CommandApiService(
                FabricLoader.getInstance().getConfigDir(),
                new MinecraftBridgeImpl(),
                modVersion(CommandApiService.MOD_ID),
                modVersion("minecraft"));
        service.start();
    }

    private static String modVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    /** The live mod instance, or null before {@link #onInitializeClient}. */
    public static CommandApiMod getInstance() {
        return instance;
    }

    public CommandApiService getService() {
        return service;
    }
}
