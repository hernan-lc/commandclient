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
 */
public class CommandApiMod implements ClientModInitializer {

    private CommandApiService service;

    @Override
    public void onInitializeClient() {
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

    public CommandApiService getService() {
        return service;
    }
}
