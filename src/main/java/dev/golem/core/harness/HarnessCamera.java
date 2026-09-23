package dev.golem.core.harness;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A client-side camera sweep for recordings: the player swings along an arc round a centre, looking at a
 * target, one step per client tick.
 *
 * <p>A server teleport per step moves the camera in visible jumps at the request rate. Here each tick moves
 * the player on the client and leaves the previous step in {@code xo/yo/zo} and {@code yRotO/xRotO}, so
 * vanilla interpolates between the two on every rendered frame and the motion is as smooth as the frame
 * rate. The player's own movement packets carry the position to the server.
 *
 * <p>The sweep is a sine: {@code angle = a0 + amplitude * sin(2 pi t / period)}. It starts where the player
 * is, slows at each end and comes back instead of jumping to the start. Any teleport, look, world change
 * or {@code orbit_stop} ends it.
 */
final class HarnessCamera {

    private static boolean active;
    private static double cx, cz, radius, y, a0, amplitude, period;
    private static double lx, ly, lz;
    private static long startTick;
    private static float lastYaw;

    private HarnessCamera() {}

    static Object start(Minecraft mc, JsonObject req) {
        LocalPlayer player = mc.player;
        if (player == null) return Json.error("no player");
        cx = Json.number(req, "cx", player.getX());
        cz = Json.number(req, "cz", player.getZ());
        double sx = Json.number(req, "x", player.getX());
        double sz = Json.number(req, "z", player.getZ());
        y = Json.number(req, "y", player.getY());
        lx = Json.number(req, "lx", cx);
        ly = Json.number(req, "ly", y);
        lz = Json.number(req, "lz", cz);
        radius = Math.hypot(sx - cx, sz - cz);
        a0 = Math.atan2(sz - cz, sx - cx);
        amplitude = Math.toRadians(Json.number(req, "amplitude", 45));
        period = Math.max(20, Json.number(req, "period", 400));
        startTick = Harness.tick();
        lastYaw = player.getYRot();
        active = true;
        // The first step is also the "previous" one: no interpolation from wherever the player was.
        step(player, 0, true);
        return null;
    }

    static void stop() {
        active = false;
    }

    static boolean isActive() {
        return active;
    }

    /** Runs at the end of every client tick: the next step becomes the current one, this one the previous. */
    static void tick(Minecraft mc) {
        if (!active) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            active = false;
            return;
        }
        step(player, Harness.tick() - startTick, false);
    }

    private static void step(LocalPlayer player, long t, boolean snap) {
        double a = a0 + amplitude * Math.sin(2 * Math.PI * t / period);
        double x = cx + radius * Math.cos(a);
        double z = cz + radius * Math.sin(a);

        double px = player.getX(), py = player.getY(), pz = player.getZ();
        float pYaw = player.getYRot(), pPitch = player.getXRot();

        player.setDeltaMovement(Vec3.ZERO);
        player.setPos(x, y, z);

        double dx = lx - x, dy = ly - (y + player.getEyeHeight()), dz = lz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        yaw = lastYaw + Mth.wrapDegrees(yaw - lastYaw);          // continuous, so interpolation never spins round
        lastYaw = yaw;
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))), -90F, 90F);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.yBodyRot = yaw;

        if (snap) {
            px = x; py = y; pz = z;
            pYaw = yaw; pPitch = pitch;
        }
        player.xo = px;
        player.yo = py;
        player.zo = pz;
        player.yRotO = pYaw;
        player.xRotO = pPitch;
        player.yHeadRotO = pYaw;
        player.yBodyRotO = pYaw;
    }
}
