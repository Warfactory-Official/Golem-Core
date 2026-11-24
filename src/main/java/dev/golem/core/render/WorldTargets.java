package dev.golem.core.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
//? if >=1.21.11 {
import org.jspecify.annotations.Nullable;
//?} else {
/*import org.jetbrains.annotations.Nullable;
*///?}
//? if >=1.21.11 && <26.3 {
import net.minecraft.client.renderer.rendertype.OutputTarget;
//?}

/**
 * The world's render targets. Fabulous splits translucent terrain and item entities into targets of their own; 26.3
 * replaced Fabulous with order-independent transparency, so there the main target holds every layer.
 */
public final class WorldTargets {

    private WorldTargets() {}

    /** Fabulous' translucent terrain target; null when the main target holds it. */
    public static @Nullable RenderTarget translucent() {
        //? if >=26.3 {
        /*return null;
        *///?} else {
        return Minecraft.getInstance().levelRenderer.getTranslucentTarget();
        //?}
    }

    /** Fabulous' item entity target; null when the main target holds it. */
    public static @Nullable RenderTarget itemEntity() {
        //? if >=26.3 {
        /*return null;
        *///?} else {
        return Minecraft.getInstance().levelRenderer.getItemEntityTarget();
        //?}
    }

    /** Whether Fabulous graphics splits the world into these targets. */
    public static boolean fabulous() {
        //? if >=26.3 {
        /*return false;
        *///?} else {
        return Minecraft.useShaderTransparency();
        //?}
    }

    //? if >=1.21.11 {
    /** Where weather draws: Fabulous' weather target, else the main target. */
    public static RenderTarget weather() {
        //? if >=26.3 {
        /*return Minecraft.getInstance().getMainRenderTarget();
        *///?} else {
        return OutputTarget.WEATHER_TARGET.getRenderTarget();
        //?}
    }
    //?}
}
