package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Fire simulation rules whose vanilla entry point changed across eras. */
public final class Fires {

    private Fires() {}

    public static boolean canSpreadAround(ServerLevel level, BlockPos pos) {
        //? if >=26.1 {
        return level.canSpreadFireAround(pos);
        //?} else {
        /*return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK)
                && level.hasChunksAt(pos.offset(-2, -2, -2), pos.offset(2, 2, 2));
        *///?}
    }

    /**
     * Whether fire can catch on {@code state} from {@code face}: the loader's flammability registry, which
     * is what vanilla fire itself consults (Forge-like loaders extend the state, Fabric keeps a registry).
     */
    public static boolean isFlammable(net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                      net.minecraft.world.level.block.state.BlockState state,
                                      net.minecraft.core.Direction face) {
        //? if forgelike {
        return state.isFlammable(level, pos, face);
        //?} elif >=26.1 {
        /*net.fabricmc.fabric.api.registry.FlammableBlockRegistry.Entry entry =
                net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        // fabric-api 11+ renamed burn/spread to igniteOdds/burnOdds; "burn" (the catch chance) is igniteOdds.
        return entry != null && entry.getIgniteOdds() > 0;
        *///?} else {
        /*net.fabricmc.fabric.api.registry.FlammableBlockRegistry.Entry entry =
                net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().get(state.getBlock());
        return entry != null && entry.getBurnChance() > 0;
        *///?}
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, int[]> STATED = new java.util.IdentityHashMap<>();

    /**
     * States a block's fire odds, vanilla's {@code FireBlock.setFlammable(block, igniteOdds, burnOdds)}:
     * {@code encouragement} is the chance fire spreads onto it, {@code flammability} the chance fire
     * consumes it. Fabric writes its flammable registry; forge-like loaders answer it from
     * {@link CompatBlock}'s fire hooks, so there it takes effect for {@code CompatBlock} subclasses only.
     * Safe to call from a block constructor.
     */
    public static void setFlammable(net.minecraft.world.level.block.Block block, int encouragement, int flammability) {
        synchronized (STATED) {
            STATED.put(block, new int[] {encouragement, flammability});
        }
        //? if fabric {
        /*net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance().add(block, encouragement, flammability);
        *///?}
    }

    /** The encouragement stated through {@link #setFlammable}, or -1 when none was. */
    public static int statedEncouragement(net.minecraft.world.level.block.Block block) {
        synchronized (STATED) {
            int[] values = STATED.get(block);
            return values == null ? -1 : values[0];
        }
    }

    /** The flammability stated through {@link #setFlammable}, or -1 when none was. */
    public static int statedFlammability(net.minecraft.world.level.block.Block block) {
        synchronized (STATED) {
            int[] values = STATED.get(block);
            return values == null ? -1 : values[1];
        }
    }
}
