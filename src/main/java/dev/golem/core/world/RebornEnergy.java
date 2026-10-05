package dev.golem.core.world;

/**
 * Fabric's energy peer: Team Reborn Energy ({@code team_reborn_energy}, the API Tech Reborn and most Fabric tech
 * mods speak), an <b>optional</b> dependency. Core compiles against it and never ships it. This class names no
 * {@code team.reborn} type (the JVM resolves those while verifying a class, before any {@code present()} check could
 * run); that code is in {@link RebornEnergyBridge}, reached only after {@link #present()} said the mod is loaded.
 *
 * <p>The unit is taken as FE 1:1, the conversion every Fabric/Forge bridge uses. Its insert and extract run inside a
 * Fabric transfer transaction the caller may abort after the call returned, so the exposed handler journals
 * through {@link EnergySource#snapshot}/{@link EnergySource#restore} exactly like 26.1 NeoForge's.
 *
 * <p>Not present on the forge-family nodes (they have the loader's own energy capability).
 */
public final class RebornEnergy {

    private RebornEnergy() {}

    /** Whether Team Reborn Energy is loaded on this (Fabric) game. */
    public static boolean present() {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("team_reborn_energy");
        *///?} else {
        return false;
        //?}
    }

    //? if fabric {
    /*/^* Only after {@link #present()}; the {@code team.reborn} code lives in {@link RebornEnergyBridge}. ^/
    static @org.jspecify.annotations.Nullable EnergyView find(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.@org.jspecify.annotations.Nullable Direction side) {
        return RebornEnergyBridge.find(level, pos, side);
    }

    /^* Only after {@link #present()}; see {@link RebornEnergyBridge}. ^/
    static void expose(EnergySource source, java.util.function.Supplier<net.minecraft.world.level.block.Block[]> blocks) {
        RebornEnergyBridge.expose(source, blocks);
    }
    *///?}
}
