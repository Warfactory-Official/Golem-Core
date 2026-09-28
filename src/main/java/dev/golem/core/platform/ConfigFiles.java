package dev.golem.core.platform;

import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;

/** Configuration paths across the File-to-Path server-directory change and the three loaders. */
public final class ConfigFiles {
    private ConfigFiles() {}

    public static Path serverConfig(MinecraftServer server, String name) {
        //? if >=1.21 {
        return server.getServerDirectory().resolve("config").resolve(name);
        //?} else {
        /*return server.getServerDirectory().toPath().resolve("config").resolve(name);
        *///?}
    }

    /**
     * The game instance's shared configuration directory, available from mod construction on both physical
     * sides (1.7 {@code FMLPreInitializationEvent.getModConfigurationDirectory()}). Unlike
     * {@link #serverConfig} it does not need a running server, so files read at start-up by the client and
     * the server alike live here.
     */
    public static Path configDir() {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        *///?} elif neoforge {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        //?} elif forge {
        /*return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?}
    }
}
