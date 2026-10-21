package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's explosion with the entity-damage factor of 1.7.10 (8, where later versions use 7). Block breaking,
 * particles, sounds and knockback are vanilla's own.
 */
public final class Explosions {

    private static final double LEGACY_FACTOR = 8.0D;

    private Explosions() {}

    /** {@link Level#explode} without fire, with 1.7 damage. */
    public static void explode(Level level, @Nullable Entity source, double x, double y, double z, float radius,
                               Level.ExplosionInteraction interaction) {
        explode(level, source, null, x, y, z, radius, false, interaction);
    }

    public static void explode(Level level, @Nullable Entity source, double x, double y, double z, float radius,
                               boolean fire, Level.ExplosionInteraction interaction) {
        explode(level, source, null, x, y, z, radius, fire, interaction);
    }

    /**
     * {@code owner} is the entity credited for the damage; it defaults to the source when that is living, or to
     * the shooter when the source is a projectile.
     */
    public static void explode(Level level, @Nullable Entity source, @Nullable LivingEntity owner, double x, double y,
                               double z, float radius, boolean fire, Level.ExplosionInteraction interaction) {
        if (!(level instanceof ServerLevel server)) {
            level.explode(source, x, y, z, radius, fire, interaction);
            return;
        }
        //? if >=1.21.1 {
        DamageSource damage = owner == null ? null : level.damageSources().explosion(source, owner);
        level.explode(source, damage, new LegacyDamage(), x, y, z, radius, fire, interaction);
        //?} else {
        /*java.util.List<Hit> hits = new java.util.ArrayList<>();
        // No damage hook here: vanilla hurts with the 7 factor and opens the invulnerability window, and a second
        // hurt with the 8 factor deals the difference.
        float reach = radius * 2.0F;
        net.minecraft.world.phys.Vec3 centre = new net.minecraft.world.phys.Vec3(x, y, z);
        for (Entity entity : level.getEntities(source, new net.minecraft.world.phys.AABB(x - reach - 1.0D,
                y - reach - 1.0D, z - reach - 1.0D, x + reach + 1.0D, y + reach + 1.0D, z + reach + 1.0D))) {
            if (!(entity instanceof LivingEntity living)) continue;
            double scaled = Math.sqrt(entity.distanceToSqr(centre)) / reach;
            if (scaled > 1.0D) continue;
            double exposed = (1.0D - scaled) * seenPercent(level, centre, entity);
            hits.add(new Hit(living, (float) (int) ((exposed * exposed + exposed) / 2.0D * LEGACY_FACTOR * reach + 1.0D)));
        }

        level.explode(source, x, y, z, radius, fire, interaction);

        LivingEntity credited = owner != null ? owner : source instanceof LivingEntity living ? living
                : source instanceof net.minecraft.world.entity.projectile.Projectile projectile
                        && projectile.getOwner() instanceof LivingEntity shooter ? shooter : null;
        DamageSource damage = level.damageSources().explosion(source, credited);
        for (Hit hit : hits) {
            if (hit.entity.isAlive() && hit.entity.invulnerableTime > 10) Compat.hurt(hit.entity, server, damage, hit.damage);
        }
        *///?}
    }

    //? if >=1.21.1 {
    private static final class LegacyDamage extends ExplosionDamageCalculator {
        // One hurt carries the final 1.7 damage.
        //? if >=26.1 {
        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float seen) {
            return legacyAmount(explosion, entity, seen);
        }
        //?} else {
        /*@Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity) {
            return legacyAmount(explosion, entity, Explosion.getSeenPercent(explosion.center(), entity));
        }
        *///?}

        private static float legacyAmount(Explosion explosion, Entity entity, float seen) {
            float reach = explosion.radius() * 2.0F;
            double scaled = Math.sqrt(entity.distanceToSqr(explosion.center())) / reach;
            double exposed = (1.0D - scaled) * seen;
            return (int) ((exposed * exposed + exposed) / 2.0D * LEGACY_FACTOR * reach + 1.0D);
        }
    }
    //?} else {
    /*
    private static double seenPercent(Level level, net.minecraft.world.phys.Vec3 centre, Entity entity) {
        // The share of the entity box that the blast centre can see, sampled like vanilla.
        net.minecraft.world.phys.AABB box = entity.getBoundingBox();
        double xs = 1.0D / ((box.maxX - box.minX) * 2.0D + 1.0D);
        double ys = 1.0D / ((box.maxY - box.minY) * 2.0D + 1.0D);
        double zs = 1.0D / ((box.maxZ - box.minZ) * 2.0D + 1.0D);
        double xOffset = (1.0D - Math.floor(1.0D / xs) * xs) / 2.0D;
        double zOffset = (1.0D - Math.floor(1.0D / zs) * zs) / 2.0D;
        int seen = 0;
        int total = 0;
        for (double xx = 0.0D; xx <= 1.0D; xx += xs) {
            for (double yy = 0.0D; yy <= 1.0D; yy += ys) {
                for (double zz = 0.0D; zz <= 1.0D; zz += zs) {
                    net.minecraft.world.phys.Vec3 sample = new net.minecraft.world.phys.Vec3(
                            net.minecraft.util.Mth.lerp(xx, box.minX, box.maxX) + xOffset,
                            net.minecraft.util.Mth.lerp(yy, box.minY, box.maxY),
                            net.minecraft.util.Mth.lerp(zz, box.minZ, box.maxZ) + zOffset);
                    if (!Raycasts.isObstructed(level, sample, centre, entity)) seen++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0D : (double) seen / total;
    }

    private record Hit(LivingEntity entity, float damage) {}
    *///?}
}
