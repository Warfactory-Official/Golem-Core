package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Alpha-tested, unculled, lightmapped entity geometry with <b>no cardinal lighting</b>: the vertex colour
 * times the lightmap: what a 1.7 renderer drew after {@code glDisable(GL_LIGHTING)}.
 *
 * <p>The stock cutout cannot stand in for it on 26.1: {@code RenderPipelines.ENTITY_CUTOUT} defines
 * {@code PER_FACE_LIGHTING}, under which {@code entity.vsh} lights the vertex twice, once from its normal
 * and once from the <i>negated</i> normal, and {@code entity.fsh} picks by {@code gl_FrontFacing}. A
 * double-sided sheet (a hanging wire, a ribbon, a flag) is a front face from one side and a back face
 * from the other, so with the pipeline unculled its far side always lands on the ambient floor of
 * {@code 0.4}. No normal a caller emits fixes that; the shader has to stop consulting it.
 *
 * <p>Below 26.1 the entity shader has no front/back split: {@code minecraft_mix_light} is evaluated once
 * per vertex from the emitted normal, so the stock no-cull cutout is used and the caller's normal decides
 * the shade. An <b>upward</b> normal {@code (0, 1, 0)} evaluates to exactly {@code 1.0} there (both level
 * light directions lean up: {@code 2 × 0.8165 × 0.6 + 0.4 > 1}, clamped), which is the flat result. Callers
 * emit that normal unconditionally; 26.1 ignores it.
 */
public final class FlatCutoutRenderType {

    private FlatCutoutRenderType() {}

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_flat_cutout")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_flat_cutout", RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .affectsCrumbling()
                    .createRenderSetup()));

    public static RenderType of(Identifier texture) {
        return BY_TEXTURE.apply(texture);
    }

    // decal: the same, drawn with a depth bias toward the camera (vanilla's polygon-offset text), for a sheet laid
    // a hair in front of a surface - 1.7 put the RBMK numitron digits a thousandth of a block over the tube glass.
    private static final RenderPipeline DECAL_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_flat_cutout_decal")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(com.mojang.blaze3d.platform.CompareOp.LESS_THAN_OR_EQUAL, true, -1.0F, -10.0F))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> DECAL_BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_flat_cutout_decal", RenderSetup.builder(DECAL_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));

    /** {@link #of}, depth-biased toward the camera so a sheet a hair in front of a surface never loses to it. */
    public static RenderType decal(Identifier texture) {
        return DECAL_BY_TEXTURE.apply(texture);
    }

    // translucent: the same unlit, lightmapped sheet blended SRC_ALPHA / ONE_MINUS_SRC_ALPHA, back faces culled and
    // depth written - 1.12's first-person crucible blade (glColor alpha 0.7 through the vertex colour). The stock
    // translucent entity pipelines all define PER_FACE_LIGHTING or leave culling off.
    private static final RenderPipeline TRANSLUCENT_CULL_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_flat_translucent_cull")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                    com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withCull(true)
            .build();

    private static final Function<Identifier, RenderType> TRANSLUCENT_CULL_BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_flat_translucent_cull", RenderSetup.builder(TRANSLUCENT_CULL_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));

    /** {@link #of}, blended translucent over what is behind it, back faces culled, depth written. */
    public static RenderType translucentCull(Identifier texture) {
        return TRANSLUCENT_CULL_BY_TEXTURE.apply(texture);
    }

    // translucent: unlit, lightmapped, blended SRC_ALPHA / ONE_MINUS_SRC_ALPHA, unculled, depth tested but not written -
    // a 1.12 particle or decal drawn with lighting off and depthMask(false) (1.12.2 ParticleBlood, ParticleDecal).
    private static final RenderPipeline TRANSLUCENT_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation("pipeline/golem_flat_translucent")
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler0")
            .withSampler("Sampler2")
            .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
            .withColorTargetState(new com.mojang.blaze3d.pipeline.ColorTargetState(
                    com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> TRANSLUCENT_BY_TEXTURE = Util.memoize(texture ->
            RenderType.create("golem_flat_translucent", RenderSetup.builder(TRANSLUCENT_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .createRenderSetup()));

    /** {@link #of}, blended translucent, unculled, without writing depth. */
    public static RenderType translucent(Identifier texture) {
        return TRANSLUCENT_BY_TEXTURE.apply(texture);
    }

    /** 26.1's pipeline ignores the normal already; nothing to rewrite. */
    static boolean isFlat(RenderType type) {
        return false;
    }

    static com.mojang.blaze3d.vertex.VertexConsumer flatten(com.mojang.blaze3d.vertex.VertexConsumer buffer) {
        return buffer;
    }
    //?} else {
    /*private static final java.util.Set<RenderType> FLAT = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final java.util.function.Function<ResourceLocation, RenderType> BY_TEXTURE =
            net.minecraft.Util.memoize(texture -> {
                RenderType type = Factory.flat(texture);
                FLAT.add(type);
                return type;
            });

    public static RenderType of(ResourceLocation texture) {
        return BY_TEXTURE.apply(texture);
    }

    private static final java.util.function.Function<ResourceLocation, RenderType> DECAL_BY_TEXTURE =
            net.minecraft.Util.memoize(texture -> {
                RenderType type = Factory.decal(texture);
                FLAT.add(type);
                return type;
            });

    /^* {@link #of}, depth-biased toward the camera (polygon offset) so a sheet a hair in front of a surface wins. ^/
    public static RenderType decal(ResourceLocation texture) {
        return DECAL_BY_TEXTURE.apply(texture);
    }

    private static final java.util.function.Function<ResourceLocation, RenderType> TRANSLUCENT_CULL_BY_TEXTURE =
            net.minecraft.Util.memoize(texture -> {
                RenderType type = Factory.translucentCull(texture);
                FLAT.add(type);
                return type;
            });

    /^* {@link #of}, blended translucent over what is behind it, back faces culled, depth written. ^/
    public static RenderType translucentCull(ResourceLocation texture) {
        return TRANSLUCENT_CULL_BY_TEXTURE.apply(texture);
    }

    private static final java.util.function.Function<ResourceLocation, RenderType> TRANSLUCENT_BY_TEXTURE =
            net.minecraft.Util.memoize(texture -> {
                RenderType type = Factory.translucent(texture);
                FLAT.add(type);
                return type;
            });

    /^* {@link #of}, blended translucent, unculled, without writing depth. ^/
    public static RenderType translucent(ResourceLocation texture) {
        return TRANSLUCENT_BY_TEXTURE.apply(texture);
    }

    private static net.minecraft.client.renderer.ShaderInstance faintShader;
    private static boolean reloadRegistered;

    // The program's apply() resets the blend on 1.20.1 (its JSON states none), so the hook run after it restores
    // the translucent blend. See NoiseDisplacedRenderType for the 1.20.1 trap.
    private static net.minecraft.client.renderer.ShaderInstance faintShader() {
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "flat_translucent", () -> {
                if (faintShader != null) faintShader.close();
                faintShader = null;
            });
        }
        if (faintShader == null) {
            //? if >=1.21 {
            String id = "golemcore:flat_translucent_legacy";
            //?} else {
            /^String id = "golemcore:flat_translucent_legacy120";
            ^///?}
            try {
                faintShader = ShaderLoader.load(net.minecraft.client.Minecraft.getInstance().getResourceManager(), id,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY, () -> {
                            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                            com.mojang.blaze3d.systems.RenderSystem.blendFuncSeparate(
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                                    com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,
                                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                        });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not load shader " + id, e);
            }
        }
        return faintShader;
    }

    /^* Whether {@code type} is one of these; its buffer must then go through {@link #flatten}. ^/
    static boolean isFlat(RenderType type) {
        return FLAT.contains(type);
    }

    /^*
     * The stock no-cull cutout, as its own instance so a replaying sink can recognise it ({@code create} and the state
     * shards are protected on {@code RenderType}, hence the subclass).
     ^/
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        static RenderType decal(ResourceLocation texture) {
            return RenderType.create("golem_flat_cutout_decal", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, true, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setLayeringState(POLYGON_OFFSET_LAYERING)
                            .createCompositeState(true));
        }

        static RenderType translucentCull(ResourceLocation texture) {
            return RenderType.create("golem_flat_translucent_cull", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, true, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true));
        }

        /^* Vanilla's entity-translucent program, discarding only at alpha 0 as 26.1's pipeline does. ^/
        private static final ShaderStateShard FAINT_TRANSLUCENT_SHADER = new ShaderStateShard(FlatCutoutRenderType::faintShader);

        static RenderType translucent(ResourceLocation texture) {
            return RenderType.create("golem_flat_translucent", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, true, true,
                    CompositeState.builder()
                            .setShaderState(FAINT_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .createCompositeState(true));
        }

        static RenderType flat(ResourceLocation texture) {
            return RenderType.create("golem_flat_cutout", com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
                    com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, 256, true, false,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(NO_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(true));
        }
    }

    /^*
     * Rewrites every normal written through {@code buffer} to the half-vector of the two shader light directions in
     * effect, which {@code minecraft_mix_light} evaluates to at least {@code 2 x 0.6 x cos(half angle) + 0.4 >= 1}
     * for every vanilla lighting setup (level, 3D items, flat items, entity in inventory): the flat result, whatever
     * pose the geometry was drawn under. A caller's up-normal only reaches that when its pose does not rotate - the
     * crucible blade in the hand does. The lights are the ones set for the draw this buffer belongs to (the level's
     * for the world and the hand, the item's for a GUI slot, which vanilla sets before the item draws).
     ^/
    static com.mojang.blaze3d.vertex.VertexConsumer flatten(com.mojang.blaze3d.vertex.VertexConsumer buffer) {
        org.joml.Vector3f[] lights = com.mojang.blaze3d.systems.RenderSystem.shaderLightDirections;
        org.joml.Vector3f half = new org.joml.Vector3f(0F, 1F, 0F);
        if (lights[0] != null && lights[1] != null) {
            org.joml.Vector3f sum = new org.joml.Vector3f(lights[0]).normalize().add(new org.joml.Vector3f(lights[1]).normalize());
            if (sum.lengthSquared() > 1.0E-6F) half = sum.normalize();
        }
        return new FlatNormals(buffer, half.x(), half.y(), half.z());
    }

    private record FlatNormals(com.mojang.blaze3d.vertex.VertexConsumer delegate, float nx, float ny, float nz)
            implements com.mojang.blaze3d.vertex.VertexConsumer {
        //? if >=1.21 {
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { delegate.setUv(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(nx, ny, nz); return this; }
        //?} else {
        /^@Override public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) { delegate.normal(nx, ny, nz); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }^/
        //?}
    }
    *///?}
}
