package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * A mod's own sky drawing in an overworld-type sky - the stand-in for 1.7's
 * {@code WorldProvider.setSkyRenderer(IRenderHandler)}, which a mod used either to replace the sky or to chain-load
 * extra objects after it.
 *
 * <p>Two seams, both inside the frame's sky pass (before terrain, depth not written):
 * <ul>
 *   <li>{@link Skybox#celestial}: replaces vanilla's sun, moon and stars for a frame. Vanilla's sky disc before it and
 *       its dark disc below the horizon after it stay. The sunrise/sunset glow is vanilla's too; a skybox that has none
 *       returns null from {@link dev.golem.core.world.Skies.Modifier#sunriseColor}.</li>
 *   <li>{@link Skybox#afterSky}: draws after the whole sky, dark disc included, on every frame the sky is drawn.</li>
 * </ul>
 *
 * <p>Hooks ({@code MixinSkyRender}): on 26.1 {@code SkyRenderer.renderSunMoonAndStars} (head, cancellable; return) and
 * {@code renderDarkDisc} (return); before 26.1 {@code LevelRenderer.renderSky} at its sun-blend call (cancellable; the
 * rest of vanilla's tail - colour reset and dark disc - is repeated) and at its final {@code depthMask(true)}. Neither
 * fires where vanilla skips the sky (fog, lava, powder snow, blindness, End/Nether skies), the same frames 1.7 skipped.
 */
public final class Skyboxes {

    private Skyboxes() {}

    public interface Skybox {
        /** True when this skybox draws {@code level}'s sun, moon and stars this frame instead of vanilla. */
        default boolean replacesCelestial(ClientLevel level) {
            return false;
        }

        /** Draws the replacement sun, moon and stars (only called after {@link #replacesCelestial} said true). */
        default void celestial(Sky sky) {}

        /** Draws after the whole sky; every frame an overworld-type sky is drawn. */
        default void afterSky(Sky sky) {}
    }

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<Skybox> SKYBOXES = dev.golem.core.hook.Hook.notifying();
    private static float lastAngle;
    private static int lastPhase;

    public static void register(Skybox skybox) {
        SKYBOXES.register(skybox);
    }

    // ======================================================================== hooks (MixinSkyRender only)

    /** Draws the first replacing skybox's celestial bodies; true when vanilla's must be skipped. */
    public static boolean celestial(PoseStack pose, float partialTick, float celestialAngle, int moonPhase) {
        lastAngle = celestialAngle;
        lastPhase = moonPhase;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SKYBOXES.isEmpty()) return false;
        for (Skybox skybox : SKYBOXES) {
            if (!skybox.replacesCelestial(level)) continue;
            Sky sky = new Sky(level, pose, partialTick, celestialAngle, moonPhase);
            try {
                skybox.celestial(sky);
            } finally {
                sky.end();
            }
            return true;
        }
        return false;
    }

    /** Runs every skybox's after-sky drawing. */
    public static void afterSky(PoseStack pose, float partialTick, float celestialAngle, int moonPhase) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || SKYBOXES.isEmpty()) return;
        Sky sky = new Sky(level, pose, partialTick, celestialAngle, moonPhase);
        try {
            for (Skybox skybox : SKYBOXES) skybox.afterSky(sky);
        } finally {
            sky.end();
        }
    }

    /** 26.1: the after-sky call, with the sun position {@link #celestial} recorded earlier in the same sky pass. */
    public static void afterSky() {
        afterSky(new PoseStack(), dev.golem.core.client.FrameTime.partialTick(), lastAngle, lastPhase);
    }

    /** 26.1: whether vanilla will draw the dark disc after the sun, moon and stars this frame. */
    public static boolean darkDiscFollows() {
        //? if >=26.1 {
        return Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.skyRenderState.shouldRenderDarkDisc;
        //?} else {
        /*return false;
        *///?}
    }
}
