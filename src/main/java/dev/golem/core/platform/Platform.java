package dev.golem.core.platform;

/**
 * Physical-side and loader questions whose spelling changed independently of Minecraft.
 */
public final class Platform {

    private Platform() {}

    /**
     * Whether this is a physical client. NeoForge moved the accessor from a static field to
     * {@code FMLLoader.getCurrent().getDist()} in 26.1; Forge and older NeoForge expose it statically.
     * Fabric answers through its own environment API.
     */
    public static boolean isClient() {
        //? if fabric {
        /*return net.fabricmc.api.EnvType.CLIENT == net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType();
        *///?} elif neoforge && >=26.1 {
        return net.neoforged.fml.loading.FMLLoader.getCurrent().getDist() == net.neoforged.api.distmarker.Dist.CLIENT;
        //?} elif neoforge {
        /*return net.neoforged.fml.loading.FMLLoader.getDist() == net.neoforged.api.distmarker.Dist.CLIENT;
        *///?} elif forge {
        /*return net.minecraftforge.fml.loading.FMLLoader.getDist() == net.minecraftforge.api.distmarker.Dist.CLIENT;
        *///?}
    }
}
