package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.PortalShape;

/**
 * Portal-frame validation and loader vetoes share one creation path.
 */
public final class Portals {
    private Portals() {
    }

    public static boolean tryCreate(ServerLevel level, BlockPos pos, Direction.Axis axis) {
        var portal = PortalShape.findEmptyPortalShape(level, pos, axis);
        //? if neoforge {
        portal = net.neoforged.neoforge.event.EventHooks.onTrySpawnPortal(level, pos, portal);
        //?} elif forge {
        /*portal = net.minecraftforge.event.ForgeEventFactory.onTrySpawnPortal(level, pos, portal);
         *///?}
        //? if >=26.1 {
        portal.ifPresent(shape -> shape.createPortalBlocks(level));
        //?} else {
        /*portal.ifPresent(PortalShape::createPortalBlocks);
         *///?}
        return portal.isPresent();
    }
}
