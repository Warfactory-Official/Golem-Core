package dev.golem.core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Small client seam for player pose changes that must happen before the humanoid model is submitted.
 *
 * <p>There is no common player-render event across the six targets. The HBM mixin only calls this hook;
 * all feature semantics remain in HBM and this class contains no mod dependency.
 */
public final class PlayerRenderHooks {

    private PlayerRenderHooks() {}

    @FunctionalInterface
    public interface Hook {
        void apply(Player player, PoseStack pose, float partialTick);
    }

    private static Hook hook = (player, pose, partialTick) -> {};
    private static java.util.function.Consumer<dev.golem.core.render.HumanoidPose> modelHook = pose -> {};

    public static void registerModel(java.util.function.Consumer<dev.golem.core.render.HumanoidPose> hook) {
        modelHook = hook == null ? pose -> {} : hook;
    }

    public static void applyModel(Player player, net.minecraft.client.model.HumanoidModel<?> model) {
        if (player == null) return;
        modelHook.accept(new dev.golem.core.render.HumanoidPose(model, player.isCrouching(),
                player.isBaby(), player.isInvisible(), player));
        // Below 26.1 the hat is a sibling, not a child of the head. PlayerModel copies the other
        // skin layers after HumanoidModel.setupAnim returns.
        //? if <26.1 {
        /*model.hat.copyFrom(model.head);
        *///?}
    }
    // Render states are frame-scoped. Weak keys cover skipped renderer paths without pinning a player or
    // a frame indefinitely, while allowing every attached HBM layer to read the same player binding.
    private static final Map<Object, Player> STATE_PLAYERS = new WeakHashMap<>();

    public static void register(Hook hook) {
        PlayerRenderHooks.hook = hook == null ? (player, pose, partialTick) -> {} : hook;
    }

    public static void apply(Player player, PoseStack pose, float partialTick) {
        if (player != null) hook.apply(player, pose, partialTick);
    }

    /** The 26.1 state renderer has no entity argument at setupRotations; local camera pose is still useful. */
    public static void applyLocal(PoseStack pose, float partialTick) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) hook.apply(player, pose, partialTick);
    }

    public static void bindState(Object state, Player player) {
        if (state != null && player != null) STATE_PLAYERS.put(state, player);
    }

    public static void applyState(Object state, PoseStack pose, float partialTick) {
        Player player = STATE_PLAYERS.get(state);
        if (player != null) hook.apply(player, pose, partialTick);
    }

    public static Player playerForState(Object state) {
        return STATE_PLAYERS.get(state);
    }
}
