package dev.golem.core.platform;

import java.nio.file.Path;

import net.minecraft.server.MinecraftServer;

/** Server configuration paths across the File-to-Path server-directory change. */
public final class ConfigFiles {
    private ConfigFiles() {}

    public static Path serverConfig(MinecraftServer server, String name) {
        //? if >=1.21 {
        return server.getServerDirectory().resolve("config").resolve(name);
        //?} else {
        /*return server.getServerDirectory().toPath().resolve("config").resolve(name);
        *///?}
    }
}
