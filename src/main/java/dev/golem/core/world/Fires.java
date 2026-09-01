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
}
