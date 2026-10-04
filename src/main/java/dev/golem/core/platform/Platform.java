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

    /** Human-readable loader name. */
    public static String name() {
        //? if fabric {
        /*return "Fabric";
        *///?} elif neoforge {
        return "NeoForge";
        //?} elif forge {
        /*return "Forge";
        *///?}
    }

    /** Whether the current loader has loaded {@code modId}. */
    public static boolean isModLoaded(String modId) {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(modId);
        *///?} elif neoforge {
        return net.neoforged.fml.ModList.get().isLoaded(modId);
        //?} elif forge {
        /*return net.minecraftforge.fml.ModList.get().isLoaded(modId);
        *///?}
    }

    /** Whether {@code player} is the loader's fake player (a machine or block acting as a player). */
    public static boolean isFakePlayer(net.minecraft.world.entity.player.Player player) {
        //? if fabric {
        /*return player instanceof net.fabricmc.fabric.api.entity.FakePlayer;
        *///?} elif neoforge {
        return player instanceof net.neoforged.neoforge.common.util.FakePlayer;
        //?} elif forge {
        /*return player instanceof net.minecraftforge.common.util.FakePlayer;
        *///?}
    }

    /** Whether the current loader is running a development environment. */
    public static boolean isDevelopment() {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment();
        *///?} elif neoforge && >=26.1 {
        return !net.neoforged.fml.loading.FMLLoader.getCurrent().isProduction();
        //?} elif neoforge {
        /*return !net.neoforged.fml.loading.FMLLoader.isProduction();
        *///?} elif forge {
        /*return !net.minecraftforge.fml.loading.FMLLoader.isProduction();
        *///?}
    }

    /**
     * Whether this Minecraft has the item-definition layer ({@code assets/<ns>/items/*.json}, 1.21.4+). Below it
     * an item's look is its {@code models/item} file alone, and a self-drawn item is one whose model chain ends at
     * {@code builtin/entity}. A data-shape fact for checks that only mean something on one side of that line;
     * asking for a marker file instead breaks as soon as that file ships on every node.
     */
    public static boolean hasItemDefinitions() {
        //? if >=1.21.4 {
        return true;
        //?} else {
        /*return false;
        *///?}
    }

    public static String environmentName() {
        return isDevelopment() ? "development" : "production";
    }
}
