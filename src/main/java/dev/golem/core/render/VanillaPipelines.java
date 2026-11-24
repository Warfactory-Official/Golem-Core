package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
//? if <26.1 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import dev.golem.core.Ids;
*///?}

/**
 * Vanilla entity pipelines by their 26.1 meaning. 26.1 renamed them with the culling swapped: its
 * {@code ENTITY_CUTOUT} is 1.21.11's {@code ENTITY_CUTOUT_NO_CULL} and its {@code ENTITY_CUTOUT_CULL} is 1.21.11's
 * {@code ENTITY_CUTOUT}, so the same spelling compiles on both and draws differently. 1.21.11 has no culled
 * translucent entity pipeline; it is built here from 26.1's definition.
 */
public final class VanillaPipelines {

    //? if >=26.1 {
    public static final RenderPipeline ENTITY_CUTOUT_CULL = RenderPipelines.ENTITY_CUTOUT_CULL;
    public static final RenderPipeline ENTITY_CUTOUT_NO_CULL = RenderPipelines.ENTITY_CUTOUT;
    public static final RenderPipeline ENTITY_CUTOUT_NO_CULL_Z_OFFSET = RenderPipelines.ENTITY_CUTOUT_Z_OFFSET;
    public static final RenderPipeline ENTITY_TRANSLUCENT_CULL = RenderPipelines.ENTITY_TRANSLUCENT_CULL;
    //?} else {
    /*public static final RenderPipeline ENTITY_CUTOUT_CULL = RenderPipelines.ENTITY_CUTOUT;
    public static final RenderPipeline ENTITY_CUTOUT_NO_CULL = RenderPipelines.ENTITY_CUTOUT_NO_CULL;
    public static final RenderPipeline ENTITY_CUTOUT_NO_CULL_Z_OFFSET = RenderPipelines.ENTITY_CUTOUT_NO_CULL_Z_OFFSET;
    public static final RenderPipeline ENTITY_TRANSLUCENT_CULL = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Ids.of("golemcore", "pipeline/entity_translucent_cull"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withSampler("Sampler1")
            .withBlend(BlendFunction.TRANSLUCENT)
            .build();
    *///?}

    private VanillaPipelines() {}
}
//?} else {
/*/^* Below 1.21.11 render types name their GL state directly. ^/
public final class VanillaPipelines {
    private VanillaPipelines() {}
}
*///?}
