package dev.golem.core.client;

import dev.golem.core.world.Skies;
//? if >=26.1 {
import net.minecraft.world.attribute.EnvironmentAttributes;
//?}

import dev.golem.core.hook.Hook;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Additions to the light map after vanilla has computed it, the stand-in for 1.7's {@code WorldProvider} light-map
 * hooks ({@code EntityRenderer.lightmapColors} changed by the provider before the dynamic texture was re-uploaded): a
 * moon lit green by its gas giant at night, a planet reddened by its neighbour's reflected light.
 *
 * <p>A {@link Modifier} gives, per cell of the 16 x 16 light map (block light level {@code block} 0..15 by sky light
 * level {@code sky} 0..15), how much red, green and blue (0..255) to add to vanilla's final colour; the sum is clamped
 * to 255, as 1.7's {@code if (color > 255) color = 255}. Additions are whole numbers, so the result is the same integer
 * colour on every node.
 *
 * <p>26.1 computes the light map on the GPU ({@code Lightmap.render}); right after vanilla's pass, a second pass draws
 * each cell's addition into the light-map texture with additive blending ({@code golemcore:core/lightmap_tint}).
 * Before 26.1 the light map is a CPU image and the additions are made to its pixels just before upload
 * ({@code LightTexture.updateLightTexture}). Both run only when vanilla updated the light map that frame and a
 * modifier is {@link Modifier#active active}. Hook: {@code MixinLightmapTint}.
 *
 * <p><b>The classic formula</b> ({@link #useClassicFormula}): 26.1 rewrote the light map (sky light without the 3 %
 * floor, a per-brightest-channel gamma curve, different block light, no final 4 % lerp to grey), so the same sky
 * brightness lights a block far darker than on 1.20.1/1.21.1. Pre-26.1 light maps follow 1.7's
 * {@code EntityRenderer.updateLightmap} term by term. For a level a mod registers, 26.1 draws the pre-26.1
 * formula's cells itself on the CPU, as {@code LightTexture.updateLightTexture} does, with the modifiers'
 * additions, over vanilla's pass; the sky brightness is {@link dev.golem.core.world.Skies#clientSkyLight}. Before
 * 26.1 it is a no-op. Not reproduced: Forge's {@code adjustLightmapColors} and the forced-bright (Nether-like) map.
 */
public final class Lightmaps {

    private Lightmaps() {}

    /** The sky input actually consumed by this node's light map and its additive tints. */
    // port: Older nodes retain native brightness and classic lightmap cells for their rendering API.
    public static float skyInput(ClientLevel level, float partialTick) {
        //? if >=26.1 {
        return Minecraft.getInstance().gameRenderer.getMainCamera().attributeProbe()
                .getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, partialTick);
        //?} else {
        /*return level.getSkyDarken(partialTick);
        *///?}
    }

    public interface Modifier {
        /** Whether this modifier changes {@code level}'s light map now. */
        boolean active(ClientLevel level);

        /**
         * Adds to {@code rgb} (red, green, blue, each 0..255, already holding earlier modifiers' additions) what this
         * modifier adds to the cell of block light {@code block} and sky light {@code sky}.
         */
        void add(ClientLevel level, int block, int sky, int[] rgb);
    }

    /** Every listener, in phase order. */
    public static final Hook<Modifier> MODIFIERS = Hook.notifying();

    public static void register(Modifier modifier) {
        MODIFIERS.register(modifier);
    }

    private static final List<Predicate<ClientLevel>> CLASSIC = new CopyOnWriteArrayList<>();

    /**
     * The levels whose light map is the pre-26.1 (1.7-shaped) formula on every node; see the class comment. For levels
     * whose sky light a mod already states in 1.7's terms ({@link dev.golem.core.world.Skies.Modifier#skyLight}).
     */
    public static void useClassicFormula(Predicate<ClientLevel> levels) {
        CLASSIC.add(levels);
    }

    /** Whether {@code level}'s light map follows the classic formula ({@link #useClassicFormula}). */
    public static boolean classic(ClientLevel level) {
        if (level == null) return false;
        if (Skies.rawSkyLightFactor(level)) return false;
        for (Predicate<ClientLevel> p : CLASSIC) if (p.test(level)) return true;
        return false;
    }

    /**
     * The pre-26.1 light map ({@code LightTexture.updateLightTexture}, 1.20.1/1.21.1 alike), cell by cell as
     * {@code [(sky * 16 + block) * 3 + channel]}, 0..255, before additions. {@code sun} is {@code getSkyDarken(1)} (the
     * sky brightness after the sky modifiers), {@code flash} whether the sky flashes ({@code getSkyFlashTime() > 0}),
     * {@code blockFactor} {@code blockLightRedFlicker + 1.5}, {@code gamma} the gamma option less the darkness effect,
     * {@code darkness} the darkness pulse, {@code nightVision} the night-vision scale and {@code bossDarkening}
     * {@code getDarkenWorldAmount}; {@code ambient} the dimension's ambient light.
     */
    public static int[] classicCells(float sun, boolean flash, float blockFactor, float gamma, float darkness,
            float nightVision, float bossDarkening, float ambient) {
        int[] out = new int[256 * 3];
        float f = sun;
        float f1 = flash ? 1.0F : f * 0.95F + 0.05F;
        // new Vector3f(f, f, 1).lerp(white, 0.35)
        float cr = f + (1.0F - f) * 0.35F, cg = cr, cb = 1.0F;
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                float f8 = classicBrightness(ambient, i) * f1;
                float f9 = classicBrightness(ambient, j) * blockFactor;
                float f10 = f9 * ((f9 * 0.6F + 0.4F) * 0.6F + 0.4F);
                float f11 = f9 * (f9 * f9 * 0.6F + 0.4F);
                float r = f9, g = f10, b = f11;
                r += cr * f8;
                g += cg * f8;
                b += cb * f8;
                r = r + (0.75F - r) * 0.04F;
                g = g + (0.75F - g) * 0.04F;
                b = b + (0.75F - b) * 0.04F;
                if (bossDarkening > 0.0F) {
                    r = r + (r * 0.7F - r) * bossDarkening;
                    g = g + (g * 0.6F - g) * bossDarkening;
                    b = b + (b * 0.6F - b) * bossDarkening;
                }
                if (nightVision > 0.0F) {
                    float max = Math.max(r, Math.max(g, b));
                    if (max < 1.0F) {
                        float scale = 1.0F / max;
                        r = r + (r * scale - r) * nightVision;
                        g = g + (g * scale - g) * nightVision;
                        b = b + (b * scale - b) * nightVision;
                    }
                }
                if (darkness > 0.0F) {
                    r -= darkness;
                    g -= darkness;
                    b -= darkness;
                }
                r = clamp01(r);
                g = clamp01(g);
                b = clamp01(b);
                r = r + (notGamma(r) - r) * gamma;
                g = g + (notGamma(g) - g) * gamma;
                b = b + (notGamma(b) - b) * gamma;
                r = clamp01(r + (0.75F - r) * 0.04F);
                g = clamp01(g + (0.75F - g) * 0.04F);
                b = clamp01(b + (0.75F - b) * 0.04F);
                int o = (i * 16 + j) * 3;
                out[o] = (int) (r * 255.0F);
                out[o + 1] = (int) (g * 255.0F);
                out[o + 2] = (int) (b * 255.0F);
            }
        }
        return out;
    }

    /** {@code LightTexture.getBrightness}: {@code lerp(ambient, l / (4 - 3 l), 1)}. */
    private static float classicBrightness(float ambient, int level) {
        float f = level / 15.0F;
        float f1 = f / (4.0F - 3.0F * f);
        return f1 + ambient * (1.0F - f1);
    }

    private static float notGamma(float value) {
        float f = 1.0F - value;
        return 1.0F - f * f * f * f;
    }

    private static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    /**
     * This frame's additions, {@code [(sky * 16 + block) * 3 + channel]}, or null when no modifier is active (or all add
     * nothing).
     */
    public static int[] additions() {
        if (MODIFIERS.isEmpty()) return null;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        int[] out = null;
        int[] rgb = new int[3];
        for (Modifier modifier : MODIFIERS) {
            if (!modifier.active(level)) continue;
            if (out == null) out = new int[256 * 3];
            for (int sky = 0; sky < 16; sky++) {
                for (int block = 0; block < 16; block++) {
                    int o = (sky * 16 + block) * 3;
                    rgb[0] = out[o];
                    rgb[1] = out[o + 1];
                    rgb[2] = out[o + 2];
                    modifier.add(level, block, sky, rgb);
                    out[o] = Math.max(0, rgb[0]);
                    out[o + 1] = Math.max(0, rgb[1]);
                    out[o + 2] = Math.max(0, rgb[2]);
                }
            }
        }
        if (out == null) return null;
        for (int value : out) if (value != 0) return out;
        return null;
    }

    // Per-version application

    //? if >=26.1 {
    private static final com.mojang.blaze3d.pipeline.RenderPipeline PIPELINE =
            com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                    .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/lightmap_tint"))
                    .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/lightmap_tint"))
                    .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/lightmap_tint"))
                    .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                            com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
                    .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                            java.util.Optional.of(com.mojang.blaze3d.pipeline.BlendFunction.ADDITIVE),
                            com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_COLOR))
                    .withCull(false).build();

    /** {@link #PIPELINE} without blending: the classic formula's cells replace vanilla's. */
    private static final com.mojang.blaze3d.pipeline.RenderPipeline CLASSIC_PIPELINE =
            com.mojang.blaze3d.pipeline.RenderPipeline.builder()
                    .withLocation(dev.golem.core.Ids.of("golemcore", "pipeline/lightmap_classic"))
                    .withVertexShader(dev.golem.core.Ids.of("golemcore", "core/lightmap_tint"))
                    .withFragmentShader(dev.golem.core.Ids.of("golemcore", "core/lightmap_tint"))
                    .withVertexFormat(com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR,
                            com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES)
                    .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                            java.util.Optional.empty(), com.mojang.blaze3d.pipeline.ColorTargetState.WRITE_COLOR))
                    .withCull(false).build();

    /**
     * 26.1, after vanilla's light-map pass: for a {@link #classic} level the classic formula's 256 cells (with the
     * additions, clamped to 255 as before 26.1) drawn over vanilla's; otherwise one additive quad per changed cell, in
     * the light map's own 16 x 16.
     */
    public static void apply(com.mojang.blaze3d.textures.GpuTextureView lightmap,
            net.minecraft.client.renderer.state.LightmapRenderState state) {
        ClientLevel level = Minecraft.getInstance().level;
        if (classic(level)) {
            float sun = dev.golem.core.world.Skies.clientSkyLight;
            // no modifier answered yet: the brightness behind vanilla's factor
            if (Float.isNaN(sun)) sun = (state.skyFactor - 0.05F) / 0.95F;
            // vanilla's lightning layer sets the (clamped) factor to 1 while the sky flashes
            boolean flash = state.skyFactor >= 1.0F && sun * 0.95F + 0.05F < 1.0F;
            int[] cells = classicCells(sun, flash, state.blockFactor + 0.1F, state.brightness, state.darknessEffectScale,
                    state.nightVisionEffectIntensity, state.bossOverlayWorldDarkening, level.dimensionType().ambientLight());
            int[] add = additions();
            if (add != null) {
                for (int k = 0; k < cells.length; k++) cells[k] = Math.min(255, cells[k] + add[k]);
            }
            draw(lightmap, CLASSIC_PIPELINE, cells, false);
            return;
        }
        int[] add = additions();
        if (add == null) return;
        draw(lightmap, PIPELINE, add, true);
    }

    /** One quad per cell of {@code add} (0..255 per channel; above 255 drawn as 255), skipping black cells if asked. */
    private static void draw(com.mojang.blaze3d.textures.GpuTextureView lightmap,
            com.mojang.blaze3d.pipeline.RenderPipeline pipeline, int[] add, boolean skipBlack) {
        var builder = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.TRIANGLES, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
        int vertices = 0;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int o = (sky * 16 + block) * 3;
                if (skipBlack && add[o] == 0 && add[o + 1] == 0 && add[o + 2] == 0) continue;
                // whole bytes, so the texture receives exactly these values
                int r = Math.min(add[o], 255), g = Math.min(add[o + 1], 255), b = Math.min(add[o + 2], 255);
                float x0 = -1.0F + block / 8.0F, x1 = x0 + 0.125F;
                float y0 = -1.0F + sky / 8.0F, y1 = y0 + 0.125F;
                builder.addVertex(x0, y0, 0).setColor(r, g, b, 255);
                builder.addVertex(x1, y0, 0).setColor(r, g, b, 255);
                builder.addVertex(x1, y1, 0).setColor(r, g, b, 255);
                builder.addVertex(x0, y0, 0).setColor(r, g, b, 255);
                builder.addVertex(x1, y1, 0).setColor(r, g, b, 255);
                builder.addVertex(x0, y1, 0).setColor(r, g, b, 255);
                vertices += 6;
            }
        }
        try (var mesh = builder.buildOrThrow()) {
            var buffer = com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
            try (var pass = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Golem light map additions", lightmap, java.util.OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setVertexBuffer(0, buffer);
                pass.draw(0, vertices);
            }
        }
    }
    //?} else {
    /*/^* Before 26.1, before the light map is uploaded: the additions made to its pixels (ABGR, red in the low byte). ^/
    public static void apply(com.mojang.blaze3d.platform.NativeImage pixels) {
        int[] add = additions();
        if (add == null) return;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                int o = (sky * 16 + block) * 3;
                if (add[o] == 0 && add[o + 1] == 0 && add[o + 2] == 0) continue;
                int abgr = pixels.getPixelRGBA(block, sky);
                int r = Math.min(255, (abgr & 0xFF) + add[o]);
                int g = Math.min(255, ((abgr >> 8) & 0xFF) + add[o + 1]);
                int b = Math.min(255, ((abgr >> 16) & 0xFF) + add[o + 2]);
                pixels.setPixelRGBA(block, sky, (abgr & 0xFF000000) | b << 16 | g << 8 | r);
            }
        }
    }
    *///?}
}
