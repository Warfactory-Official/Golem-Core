package dev.golem.core.world;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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

    /** Returns hittable entities crossed by a projectile segment, nearest first. */
    public static List<Entity> sweepEntities(Level level, Entity except, Vec3 from, Vec3 to,
                                             double inflate, Predicate<? super Entity> filter) {
        List<EntityDistance> hits = new ArrayList<>();
        for (Entity entity : level.getEntities(except, new AABB(from, to).inflate(inflate),
                candidate -> candidate.isPickable() && filter.test(candidate))) {
            Optional<Vec3> location = entity.getBoundingBox().inflate(inflate).clip(from, to);
            if (location.isPresent()) hits.add(new EntityDistance(entity, location.get().distanceToSqr(from)));
        }
        hits.sort((a, b) -> Double.compare(a.distance, b.distance));
        return hits.stream().map(hit -> hit.entity).toList();
    }

    /** Pure segment/AABB contract used by projectile regression tests. */
    public static boolean segmentIntersects(AABB box, Vec3 from, Vec3 to, double inflate) {
        return box.inflate(inflate).clip(from, to).isPresent();
    }

    private record EntityDistance(Entity entity, double distance) {}
}
