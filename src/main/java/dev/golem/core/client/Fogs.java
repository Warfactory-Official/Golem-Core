package dev.golem.core.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime changes to the world fog as the legacy {@code EntityViewRenderEvent.FogColors} and {@code FogDensity}
 * events made them: the <b>final</b> fog colour (after vanilla has blended the sky, the weather and the camera's
 * fluid into it), and a linear, spherical fog from 0 to a chosen distance in place of vanilla's.
 *
 * <p>How it is applied: NeoForge and Forge from {@code ViewportEvent.ComputeFogColor} and {@code ViewportEvent.RenderFog}
 * (cancelled below 26.1, where that is how the event applies its distances); Fabric, which has no fog event, from
 * {@code MixinFogRendererModifiers} at the same two places. On 26.1 the distance replaces the environmental fog and
 * caps the sky and cloud fog the way vanilla's own world fog (the boss-bar fog) does; below 26.1 it is the shader fog of
 * both the sky and the terrain pass. Nothing changes unless a modifier is {@link Modifier#active active}.
 */
public final class Fogs {

    private Fogs() {}

    public interface Modifier {
        /** Whether this modifier changes the fog seen from {@code camera} (the camera entity) this frame. */
        boolean active(Entity camera);

        /** The fog colour to use, from vanilla's final one; RGB in 0..1. */
        default Vec3 color(Entity camera, Vec3 rgb) { return rgb; }

        /**
         * The far end of a linear fog that starts at the camera, or {@code NaN} to keep vanilla's distances.
         * {@code renderDistanceBlocks} is the render distance in blocks.
         */
        default float far(Entity camera, float renderDistanceBlocks) { return Float.NaN; }
    }

    private static final List<Modifier> MODIFIERS = new CopyOnWriteArrayList<>();
    private static boolean hooked;

    public static synchronized void addModifier(Modifier modifier) {
        MODIFIERS.add(modifier);
        if (hooked) return;
        hooked = true;
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW,
                (net.neoforged.neoforge.client.event.ViewportEvent.ComputeFogColor event) -> {
                    Entity camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
                    if (!active(camera)) return;
                    Vec3 out = color(camera, new Vec3(event.getRed(), event.getGreen(), event.getBlue()));
                    event.setRed((float) out.x);
                    event.setGreen((float) out.y);
                    event.setBlue((float) out.z);
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW,
                (net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) -> {
                    Entity camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
                    float far = far(camera, renderDistanceBlocks());
                    if (Float.isNaN(far)) return;
                    //? if >=26.1 {
                    net.minecraft.client.renderer.fog.FogData data = event.getFogData();
                    data.environmentalStart = 0.0F;
                    data.environmentalEnd = far;
                    data.skyEnd = Math.min(data.skyEnd, far);
                    data.cloudEnd = Math.min(data.cloudEnd, far);
                    //?} else {
                    /*event.setNearPlaneDistance(0.0F);
                    event.setFarPlaneDistance(far);
                    event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
                    event.setCanceled(true);
                    *///?}
                });
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW,
                (net.minecraftforge.client.event.ViewportEvent.ComputeFogColor event) -> {
                    Entity camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
                    if (!active(camera)) return;
                    Vec3 out = color(camera, new Vec3(event.getRed(), event.getGreen(), event.getBlue()));
                    event.setRed((float) out.x);
                    event.setGreen((float) out.y);
                    event.setBlue((float) out.z);
                });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOW,
                (net.minecraftforge.client.event.ViewportEvent.RenderFog event) -> {
                    Entity camera = net.minecraft.client.Minecraft.getInstance().getCameraEntity();
                    float far = far(camera, renderDistanceBlocks());
                    if (Float.isNaN(far)) return;
                    event.setNearPlaneDistance(0.0F);
                    event.setFarPlaneDistance(far);
                    event.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
                    event.setCanceled(true);
                });
        *///?}
    }

    /** The render distance in blocks, as the fog code reads it ({@code options.renderDistance * 16}). */
    public static float renderDistanceBlocks() {
        return net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
    }

    public static boolean active(Entity camera) {
        if (MODIFIERS.isEmpty() || camera == null) return false;
        for (Modifier modifier : MODIFIERS) if (modifier.active(camera)) return true;
        return false;
    }

    public static Vec3 color(Entity camera, Vec3 rgb) {
        for (Modifier modifier : MODIFIERS) if (modifier.active(camera)) rgb = modifier.color(camera, rgb);
        return rgb;
    }

    /** The nearest far distance any active modifier asks for, or NaN when none does. */
    public static float far(Entity camera, float renderDistanceBlocks) {
        float out = Float.NaN;
        if (MODIFIERS.isEmpty() || camera == null) return out;
        for (Modifier modifier : MODIFIERS) {
            if (!modifier.active(camera)) continue;
            float far = modifier.far(camera, renderDistanceBlocks);
            if (!Float.isNaN(far) && (Float.isNaN(out) || far < out)) out = far;
        }
        return out;
    }
}
