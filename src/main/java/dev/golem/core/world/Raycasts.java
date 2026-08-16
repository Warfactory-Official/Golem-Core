package dev.golem.core.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Block raycasts whose context constructor changed when item components landed. */
public final class Raycasts {

    private Raycasts() {}

    /** Returns whether a collidable block lies between the two points. */
    public static boolean isObstructed(Level level, Vec3 from, Vec3 to, Entity contextEntity) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                //? if >=1.20.5 {
                net.minecraft.world.phys.shapes.CollisionContext.empty()
                //?} else {
                /*contextEntity
                *///?}
                )).getType() != HitResult.Type.MISS;
    }
}
