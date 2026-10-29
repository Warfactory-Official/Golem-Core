package dev.golem.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.golem.core.render.HumanoidPose;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

//? if >=26.1 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.common.NeoForge;
//?}

/**
 * Frame bindings keep player model and layer callbacks attached to the same rendered player.
 */
public final class PlayerRenderHooks {

    private PlayerRenderHooks() {
    }

    private static final dev.golem.core.hook.Hook<Predicate<Player>> FLIGHT_POSES =
            dev.golem.core.hook.Hook.of(listeners -> player -> {
                for (int index = 0; index < listeners.size(); index++)
                    if (listeners.get(index).test(player)) return true;
                return false;
            });
    private static boolean flightEventsInstalled;

    /**
     * Requests a fully blended fall-flying render pose without changing travel or velocity yaw.
     */
    public static synchronized void flightPose(Predicate<Player> condition) {
        FLIGHT_POSES.register(condition);
        if (flightEventsInstalled) return;
        flightEventsInstalled = true;
        //? if neoforge && >=26.1 {
        NeoForge.EVENT_BUS.addListener((RenderPlayerEvent.Pre<?> event) -> applyFlightState(event.getRenderState()));
        //?}
    }

    //? if >=26.1 {
    public static void applyFlightState(AvatarRenderState state) {
        var player = playerForState(state);
        if (player != null && FLIGHT_POSES.invoker().test(player)) {
            state.isFallFlying = true;
            state.fallFlyingTimeInTicks = 10F;
            state.shouldApplyFlyingYRot = false;
        }
    }
    //?}

    public static void applyFlightRotation(Player player, PoseStack pose) {
        if (FLIGHT_POSES.invoker().test(player))
            pose.mulPose(Axis.XP.rotationDegrees(-90F - player.getXRot()));
    }

    @FunctionalInterface
    public interface Hook {
        void apply(Player player, PoseStack pose, float partialTick);
    }

    /**
     * Every listener that changes the humanoid model's parts after {@code setupAnim}, in phase order, each seeing the
     * previous one's parts (HBM: the glider's flight freeze, then the gun arm pose). A list, not a slot: a second mod's
     * listener joins the first's instead of replacing it.
     */
    public static final dev.golem.core.hook.Hook<Consumer<HumanoidPose>> MODEL =
            dev.golem.core.hook.Hook.notifying();

    public static void applyModel(Player player, HumanoidModel<?> model) {
        if (player == null) return;
        if (!MODEL.isEmpty()) {
            HumanoidPose parts = new HumanoidPose(model, player.isCrouching(),
                    player.isBaby(), player.isInvisible(), player);
            for (Consumer<HumanoidPose> listener : MODEL) listener.accept(parts);
        }
        // Below 26.1 the hat is a sibling, not a child of the head. PlayerModel copies the other
        // skin layers after HumanoidModel.setupAnim returns.
        //? if <26.1 {
        /*model.hat.copyFrom(model.head);
         *///?}
    }

    // Render states are frame-scoped. Weak keys cover skipped renderer paths without pinning a player or
    // a frame indefinitely, while allowing every attached HBM layer to read the same player binding.
    private static final Map<Object, Player> STATE_PLAYERS = new WeakHashMap<>();

    /**
     * Every pose listener, in phase order: each may transform the player's whole model (1.7 {@code RenderPlayerEvent.Pre}
     * with a {@code glRotate}). The pose is the render pose at the player's position, relative to the camera;
     * {@link #renderOffset} gives that offset in world axes. HBM's own pose (the jetpack/glider) is in its phase
     * {@code hbm:player}, ordered before the default phase.
     */
    public static final dev.golem.core.hook.Hook<Hook> POSE = dev.golem.core.hook.Hook.notifying();

    private static void run(Player player, PoseStack pose, float partialTick) {
        if (!POSE.isEmpty()) for (Hook listener : POSE) listener.apply(player, pose, partialTick);
    }

    /**
     * The player's render position relative to the camera, in world axes: 1.7's {@code (x, y, z)} of
     * {@code RenderPlayer.doRender}, where a {@code RenderPlayerEvent.Pre} transform was applied before the renderer
     * translated there. To rotate about the camera as 1.7 did, translate by its negation, rotate, translate back.
     */
    public static net.minecraft.world.phys.Vec3 renderOffset(Player player, float partialTick) {
        //? if >=26.1 {
        net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera()
                                                                                                        .position();
        //?} else {
        /*net.minecraft.world.phys.Vec3 camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
         *///?}
        return player.getPosition(partialTick).subtract(camera);
    }

    public static void apply(Player player, PoseStack pose, float partialTick) {
        if (player != null) run(player, pose, partialTick);
    }

    /**
     * The 26.1 state renderer has no entity argument at setupRotations; local camera pose is still useful.
     */
    public static void applyLocal(PoseStack pose, float partialTick) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) run(player, pose, partialTick);
    }

    public static void bindState(Object state, Player player) {
        if (state != null && player != null) STATE_PLAYERS.put(state, player);
    }

    public static void applyState(Object state, PoseStack pose, float partialTick) {
        Player player = STATE_PLAYERS.get(state);
        if (player != null) run(player, pose, partialTick);
    }

    public static Player playerForState(Object state) {
        return STATE_PLAYERS.get(state);
    }
}
