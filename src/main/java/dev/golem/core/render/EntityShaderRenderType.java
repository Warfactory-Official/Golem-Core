package dev.golem.core.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.Map;

/**
 * {@code entityCutoutCull} with a mod fragment shader and one extra texture, {@code Sampler3}, for 1.12 renderers
 * that bound their own program over a model draw (the jade shotgun's {@code flash_lmap}). The vertex stage stays
 * vanilla's entity shader.
 * <p>Resources: {@code shaders/core/<name>.fsh} on 26.1; below it a {@code <name>.json} program over
 * {@code rendertype_entity_cutout} that must list every uniform that vertex stage reads ({@code IViewRotMat} is
 * 1.20.1-only).
 */
public final class EntityShaderRenderType {

    private EntityShaderRenderType() {}

    private static final Map<String, RenderType> TYPES = new HashMap<>();

    //? if >=26.1 {
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();

    public static RenderType cutoutCull(String shaderId, Identifier texture, Identifier extra) {
        return TYPES.computeIfAbsent(shaderId + "\n" + texture + "\n" + extra, key -> {
            RenderPipeline pipeline = PIPELINES.computeIfAbsent(shaderId, id -> {
                Identifier base = dev.golem.core.Ids.parse(id);
                return WorldPipelines.add(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                        .withLocation(dev.golem.core.Ids.of(base.getNamespace(), "pipeline/" + base.getPath()))
                        .withVertexShader("core/entity")
                        .withFragmentShader(dev.golem.core.Ids.of(base.getNamespace(), "core/" + base.getPath()))
                        .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                        .withShaderDefine("NO_OVERLAY")
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withSampler("Sampler3")
                        .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS)
                        .withDepthStencilState(DepthStencilState.DEFAULT)
                        .withCull(true)
                        .build());
            });
            return RenderType.create("golem_entity_shader", RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", texture)
                    .withTexture("Sampler3", extra, NoiseTextures::sampler)
                    .useLightmap()
                    .createRenderSetup());
        });
    }
    //?} else {
    /*private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static boolean reloadRegistered;

    public static RenderType cutoutCull(String shaderId, ResourceLocation texture, ResourceLocation extra) {
        if (!reloadRegistered) {
            reloadRegistered = true;
            dev.golem.core.client.ClientResourceReloads.register("golemcore", "entity_shader", () -> {
                SHADERS.values().forEach(ShaderInstance::close);
                SHADERS.clear();
                TYPES.clear();
            });
        }
        return TYPES.computeIfAbsent(shaderId + "\n" + texture + "\n" + extra,
                key -> Factory.create(shaderId, texture, extra));
    }

    private static ShaderInstance shader(String shaderId) {
        return SHADERS.computeIfAbsent(shaderId, id -> {
            try {
                return ShaderLoader.load(Minecraft.getInstance().getResourceManager(), id, DefaultVertexFormat.NEW_ENTITY);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not load shader " + id, e);
            }
        });
    }

    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        /^* The base texture on unit 0 as any entity type, the extra one on unit 3. ^/
        private static final class Textures extends TextureStateShard {
            private final ResourceLocation extra;

            private Textures(ResourceLocation texture, ResourceLocation extra) {
                super(texture, false, false);
                this.extra = extra;
            }

            @Override
            public void setupRenderState() {
                super.setupRenderState();
                com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(3, extra);
                NoiseTextures.apply(Minecraft.getInstance().getTextureManager().getTexture(extra));
            }
        }

        static RenderType create(String shaderId, ResourceLocation texture, ResourceLocation extra) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader(shaderId)))
                    .setTextureState(new Textures(texture, extra))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true);
            return RenderType.create("golem_entity_shader", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    true, false, state);
        }
    }
    *///?}
}
