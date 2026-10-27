package dev.golem.core.world;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
//? if >=1.21 && <26.1 {
/*import net.minecraft.world.level.portal.DimensionTransition;
*///?}
//? if >=26.1 {
import net.minecraft.world.level.portal.TeleportTransition;
//?}
import net.minecraft.world.phys.Vec3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** Whether the server is still waiting for this player to acknowledge a teleport. */
    public static boolean awaitingAcknowledgement(ServerPlayer player) {
        return player.connection.awaitingPositionFromClient != null;
    }

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
     * Whether cross-level moves must be deferred to the end-of-tick worker: true where vanilla has no passenger
     * transition (below 1.21.1), so callers queue instead of moving immediately.
     */
    public static boolean defersTransfers() {
        //? if >=1.21.1 {
        return false;
        //?} else {
        /*return true;
        *///?}
    }

    /**
     * Moves the root vehicle and its passengers to {@code target} at {@code pos}, keeping velocity and rotation, and
     * calls {@code afterPassengers} once vanilla has put the passengers back aboard. Returns false where vanilla has
     * no passenger transition (below 1.21.1): the caller keeps its legacy separate-move path.
     */
    public static boolean transferRoot(Entity root, ServerLevel target, Vec3 pos,
                                       Consumer<Entity> afterPassengers) {
        //? if >=26.1 {
        root.teleport(new TeleportTransition(target, pos, root.getDeltaMovement(),
                root.getYRot(), root.getXRot(), afterPassengers::accept));
        return true;
        //?} elif >=1.21.1 {
        /*root.changeDimension(new DimensionTransition(target, pos, root.getDeltaMovement(),
                root.getYRot(), root.getXRot(), afterPassengers::accept));
        return true;
        *///?} else {
        /*return false;
        *///?}
    }

    /**
     * Cross-level move that keeps velocity and leaves a portal ticket at the arrival, as a teleporter crossing does.
     */
    public static void transferWithTicket(Entity entity, ServerLevel destination, Vec3 pos) {
        //? if >=26.1 {
        entity.teleport(new TeleportTransition(destination, pos, entity.getDeltaMovement(),
                entity.getYRot(), entity.getXRot(),
                TeleportTransition.PLACE_PORTAL_TICKET));
        //?} elif >=1.21.1 {
        /*entity.changeDimension(new DimensionTransition(destination, pos, entity.getDeltaMovement(),
                entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLACE_PORTAL_TICKET));
        *///?} else {
        /*var arrival = BlockPos.containing(pos.x, pos.y, pos.z);
        destination.getChunk(arrival);
        if (toLevel(entity, destination, pos.x, pos.y, pos.z)) {
            destination.getChunkSource().addRegionTicket(TicketType.PORTAL,
                    new ChunkPos(arrival), 3, arrival);
        }
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
