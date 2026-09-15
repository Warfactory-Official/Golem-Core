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

    /** Alpha-tested entity geometry with face culling enabled, matching the old GL default. */
    public static RenderType entityCutoutCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutoutCull(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);
        *///?}
    }

    /** An alpha-tested entity pass with face culling disabled, for legacy double-sided OBJ geometry. */
    public static RenderType entityCutoutNoCull(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
        texture) {
        //? if >=26.1 {
        // 26.1's entityCutout is the no-cull pipeline; entityCutoutCull is the opt-in culling variant.
        return RenderTypes.entityCutout(texture);
        //?} else {
        /*return RenderType.entityCutoutNoCull(texture);
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

    /** The vanilla line pipeline, moved alongside the other render-type factories in 26.1. */
    public static RenderType lines() {
        //? if >=26.1 {
        return RenderTypes.lines();
        //?} else {
        /*return RenderType.lines();
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

    /** Torex's legacy fog-free, lightmapped {@code SRC_ALPHA/ONE} flare with no depth write. */
    public static RenderType torexFlare(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        return TorexFlareRenderType.of(texture);
    }

    public record FlareVertex(float red, float green, float blue, float alpha, int packedLight) {}

    /** Adapts the old flare row to the render type available on this target. */
    public static FlareVertex torexFlareVertex(float red, float green, float blue,
                                                float alpha, int packedLight) {
        //? if >=26.1 {
        return new FlareVertex(red, green, blue, alpha, packedLight);
        //?} else {
        /*return new FlareVertex(red * alpha, green * alpha, blue * alpha, 1F, 0x00F000F0);
        *///?}
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

    /** Textureless alpha-weighted additive sheets, matching vanilla's lightning pipeline. */
    public static RenderType lightning() {
        return LightningRenderType.get();
    }

    /** Textureless, translucent, depth-tested smoke sheets with no depth writes. */
    public static RenderType weaponSmoke() {
        return WeaponSmokeRenderType.get();
    }
}
