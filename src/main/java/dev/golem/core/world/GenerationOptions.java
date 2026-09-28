package dev.golem.core.world;

import net.minecraft.server.MinecraftServer;

/**
 * The world's generation options, read one way on every version.
 *
 * <p>26.1 moved the options off {@code WorldData.worldGenOptions()} onto the server's
 * {@code getWorldGenSettings().options()}; the values are the same.
 */
public final class GenerationOptions {

    private GenerationOptions() {}

    /** The "generate structures" world option (1.7's {@code isMapFeaturesEnabled}). */
    public static boolean structures(MinecraftServer server) {
        //? if >=26.1 {
        return server.getWorldGenSettings().options().generateStructures();
        //?} else {
        /*return server.getWorldData().worldGenOptions().generateStructures();
        *///?}
    }
}
