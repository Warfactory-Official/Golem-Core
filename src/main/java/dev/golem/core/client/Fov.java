package dev.golem.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * A client-side callback for changing the computed camera FOV, and the FOV the world is being drawn with this frame
 * ({@link #current}).
 */
public final class Fov {

    private Fov() {}

    @FunctionalInterface
    public interface Modifier {
        float apply(ItemStack held, float fov, float partialTick);
    }

    private static Modifier modifier = (held, fov, partialTick) -> fov;
    private static boolean registered;

    /** Installs the one client FOV callback used by the mod. It changes the level's FOV, never the hand's. */
    public static void register(Modifier modifier) {
        if (registered) return;
        registered = true;
        Fov.modifier = modifier;

        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) -> {
                    if (event.usedConfiguredFov()) event.setFOV(apply((float) event.getFOV(), (float) event.getPartialTick()));
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ViewportEvent.ComputeFov event) -> {
                    if (event.usedConfiguredFov()) event.setFOV(apply((float) event.getFOV(), (float) event.getPartialTick()));
                });
        *///?}
    }

    private static Modifier multiplier = (held, fov, partialTick) -> fov;
    private static boolean multiplierRegistered;

    /**
     * Installs the one callback on the local player's FOV multiplier, 1.7's {@code FOVUpdateEvent}: it runs once a
     * tick, and vanilla eases the camera halfway to the result each tick. {@code partialTick} is the last frame's.
     */
    public static void registerMultiplier(Modifier modifier) {
        if (multiplierRegistered) return;
        multiplierRegistered = true;
        multiplier = modifier;

        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ComputeFovModifierEvent event) -> {
                    if (event.getPlayer() == Minecraft.getInstance().player)
                        event.setNewFovModifier(applyMultiplier(event.getNewFovModifier()));
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ComputeFovModifierEvent event) -> {
                    if (event.getPlayer() == Minecraft.getInstance().player)
                        event.setNewFovModifier(applyMultiplier(event.getNewFovModifier()));
                });
        *///?}
    }

    /** Applies {@link #registerMultiplier}'s callback to the local player's FOV multiplier for this tick. */
    public static float applyMultiplier(float fovMultiplier) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return multiplier.apply(held, fovMultiplier, Frames.partialTick());
    }

    private static volatile float lastWorldFov = Float.NaN;
    private static boolean recording;

    /**
     * The field of view, in degrees, the level is drawn with this frame: the options' FOV after vanilla's dynamic
     * modifier (sprinting, drawn bow, scoping), death and under-fluid narrowing, the loaders' FOV events and
     * {@link #register}'s callback. Mirrors 1.7's {@code EntityRenderer.getFOVModifier(partialTicks, true)}.
     * 26.1 keeps it on {@code Camera.getFov()}. Before 26.1 {@code GameRenderer.getFov} is private, so the value it
     * returns once per frame is recorded (Fabric: the FOV mixin; Forge/NeoForge: a lowest-priority {@code ComputeFov}
     * listener installed on the first call, so the very first frame reads the options' FOV).
     */
    public static float current() {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.1 {
        return minecraft.gameRenderer.getMainCamera().getFov();
        //?} else {
        /*if (!recording) {
            recording = true;
            //? if neoforge {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                    (net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) -> {
                        if (event.usedConfiguredFov()) record(event.getFOV());
                    });
            //?} elif forge {
            /^net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,
                    (net.minecraftforge.client.event.ViewportEvent.ComputeFov event) -> {
                        if (event.usedConfiguredFov()) record(event.getFOV());
                    });
            ^///?}
        }
        float fov = lastWorldFov;
        return Float.isNaN(fov) ? minecraft.options.fov().get().floatValue() : fov;
        *///?}
    }

    /** Records the level FOV vanilla computed this frame (before 26.1; see {@link #current}). */
    public static void record(double fov) {
        lastWorldFov = (float) fov;
    }

    /** Applies the installed callback to the local player's main-hand item. */
    public static float apply(float fov, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return modifier.apply(held, fov, partialTick);
    }
}
