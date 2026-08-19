package dev.golem.core.render;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * The render types block-entity renderers draw with.
 *
 * <p>26.1 moved these factories out of {@code RenderType} into a separate {@code RenderTypes} holder
 * and into the {@code ...renderer.rendertype} package. The factories themselves are unchanged, so this
 * is purely a naming shim — but it is a shim worth having as real code rather than a text replacement:
 * a substring rename of {@code RenderType} would also mangle every symbol that merely contains it.
 */
public final class CompatRenderTypes {

    private CompatRenderTypes() {}


    public static RenderType entitySolid(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entitySolid(texture);
        //?} else {
        /*return RenderType.entitySolid(texture);
        *///?}
    }

    public static RenderType entityCutout(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutout(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);
        *///?}
    }

    public static RenderType entityTranslucent(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucent(texture);
        //?} else {
        /*return RenderType.entityTranslucent(texture);
        *///?}
    }

    public static RenderType entityTranslucentEmissive(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucentEmissive(texture);
        //?} else {
        /*return RenderType.entityTranslucentEmissive(texture);
        *///?}
    }

    /**
     * A self-lit glow drawn over solid geometry — alpha-weighted additive, depth-tested but not
     * depth-writing, unculled. See {@link PlasmaGlowRenderType} for why no stock type fits.
     */
    public static RenderType plasmaGlow(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return PlasmaGlowRenderType.of(texture);
    }

    /** Translucent variant with explicit outline control; both eras expose the same two-arg form. */
    public static RenderType entityTranslucent(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture, boolean outline) {
        //? if >=26.1 {
        return RenderTypes.entityTranslucent(texture, outline);
        //?} else {
        /*return RenderType.entityTranslucent(texture, outline);
        *///?}
    }

    public static RenderType energySwirl(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture, float u, float v) {
        //? if >=26.1 {
        return RenderTypes.energySwirl(texture, u, v);
        //?} else {
        /*return RenderType.energySwirl(texture, u, v);
        *///?}
    }

    public static RenderType dragonRays() {
        //? if >=26.1 {
        return RenderTypes.dragonRays();
        //?} else {
        /*return RenderType.lightning();
        *///?}
    }
}
