package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Moving an entity to a position, possibly in another level, keeping its rotation.
 *
 * <p>Vanilla has had one method for this since 1.19.4, the one {@code /tp} calls, but 1.21.2 added a
 * trailing "reset the camera" flag and renamed the relative-movement set's element type. Passing an
 * empty set keeps the element type out of this signature, so the only difference left is the flag.
 */
public final class Teleports {

    private Teleports() {}

    /**
     * Teleports {@code entity} to {@code (x, y, z)} in {@code level}, which may be the level it is in.
     * A player is moved with its connection and camera; any other entity is recreated in the target
     * level by vanilla, exactly as {@code /tp} does. Returns whether vanilla accepted the move.
     */
    public static boolean toLevel(Entity entity, ServerLevel level, double x, double y, double z) {
        //? if >=26.1 {
        return entity.teleportTo(level, x, y, z, java.util.Set.of(), entity.getYRot(), entity.getXRot(), true);
        //?} else {
        /*return entity.teleportTo(level, x, y, z, java.util.Set.of(), entity.getYRot(), entity.getXRot());
        *///?}
    }

    /**
     * Puts {@code passenger} back on {@code vehicle} after both were teleported separately. Dismounting gives the
     * passenger vanilla's 60-tick boarding cooldown, which refuses an ordinary {@code startRiding}, so this mounts by
     * force, as vanilla's own cross-level vehicle move remounts its passengers. 26.1 added a trailing "send the mount
     * event and triggers" flag (kept on). Returns whether the passenger now rides the vehicle.
     */
    public static boolean remount(Entity passenger, Entity vehicle) {
        //? if >=26.1 {
        return passenger.startRiding(vehicle, true, true);
        //?} else {
        /*return passenger.startRiding(vehicle, true);
        *///?}
    }

    /**
     * The world's spawn point (1.7 {@code getSpawnPoint}, kept with the overworld): where a player without a bed
     * respawns. 26.1 keeps it as the server's respawn data, before 26.1 the overworld's shared spawn position.
     */
    public static net.minecraft.core.BlockPos worldSpawn(net.minecraft.server.MinecraftServer server) {
        //? if >=26.1 {
        return server.overworld().getRespawnData().globalPos().pos();
        //?} else {
        /*return server.overworld().getSharedSpawnPos();
        *///?}
    }
}
