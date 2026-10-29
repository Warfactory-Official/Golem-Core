package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

//? if >=26.1 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
//?} else {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
*///?}

/**
 * A block state's model drawn as a free-standing ghost (a placement preview) in one flat colour.
 *
 * <p>This is the state a 1.7 {@code RenderWorldLastEvent} preview drew under: {@code disableColor()}
 * plus one {@code glColor}, alpha test at 0.1, the lightmap unit off. No ambient occlusion, face
 * shade, tint or world light: every vertex carries the same colour and the texture's own alpha
 * decides what is cut out. Both halves are era-specific:
 *
 * <ul>
 *   <li>{@link #renderType()}: 26.1 declares a pipeline on the entity shader with the defines that
 *       switch its directional and per-face lighting off; older versions reach the same flat,
 *       depth-writing, culled state through the emissive entity shader in a {@code CompositeState}.
 *       No stock type has that combination on either era.</li>
 *   <li>{@link #bake}: 26.1 collects {@code BlockStateModelPart}s from the {@code BlockStateModelSet}
 *       and writes them through a {@code QuadInstance}; older versions read the {@code BakedModel}'s
 *       packed {@code BakedQuad} ints directly, because their {@code putBulkData} hard-codes alpha 1.</li>
 * </ul>
 *
 * <p>The model is resolved from the state alone (its properties), not from neighbours: a preview's
 * cells are not in the level, so a caller whose block reads neighbours at model time must fold that
 * into the state first.
 */
public final class GhostBlocks {

    /** Quads baked once; emitted per frame at whatever pose the caller supplies. */
    @FunctionalInterface
    public interface Geometry {
        void emit(PoseStack.Pose pose, VertexConsumer buffer);
    }

    private static final Geometry EMPTY = (pose, buffer) -> {};

    private GhostBlocks() {}

    //? if >=26.1 {
    private static final RenderPipeline PIPELINE = WorldPipelines.add(RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
            .withLocation("pipeline/golem_ghost_block")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withSampler("Sampler1")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());

    private static final RenderType TYPE = RenderType.create("golem_ghost_block",
            RenderSetup.builder(PIPELINE)
                    .withTexture("Sampler0", TextureAtlas.LOCATION_BLOCKS)
                    .useOverlay()
                    .createRenderSetup());
    //?} else {
    /*private static final RenderType TYPE = Factory.ghost();

    /^* {@code create} is protected on {@code RenderType}; the subclass is the long-standing way in. ^/
    private static final class Factory extends RenderType {
        private Factory() { super(null, null, null, 0, false, false, null, null); }

        static RenderType ghost() {
            return create("golem_ghost_block", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, false, CompositeState.builder()
                            // Flat vertex colour, no lightmap, alpha test at 0.1 - the emissive entity shader.
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                            .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(CULL)
                            .setWriteMaskState(COLOR_DEPTH_WRITE)
                            .setOverlayState(OVERLAY)
                            .createCompositeState(false));
        }
    }
    *///?}

    /**
     * Flat-coloured, full-bright, alpha-cutout (0.1), depth-writing, back-face-culled quads on the
     * block atlas. Alpha blends, so an {@code argb} below {@code 0xFF} alpha reads as translucent.
     */
    public static RenderType renderType() {
        return TYPE;
    }

    /**
     * Bakes {@code state}'s model as it would stand at {@code pos} (which seeds the model's random
     * variant) with every vertex coloured {@code argb}. The result holds no references to the level and
     * may be emitted on any later frame.
     */
    public static Geometry bake(BlockState state, BlockPos pos, int argb) {
        if (state.isAir()) return EMPTY;
        RandomSource random = RandomSource.create();
        random.setSeed(state.getSeed(pos));
        //? if >=26.1 {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>(1);
        model.collectParts(random, parts);
        if (parts.isEmpty()) return EMPTY;

        QuadInstance instance = new QuadInstance();
        instance.setColor(argb);
        instance.setLightCoords(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        return (pose, buffer) -> {
            for (BlockStateModelPart part : parts) {
                for (Direction direction : Direction.values()) {
                    for (BakedQuad quad : part.getQuads(direction)) buffer.putBakedQuad(pose, quad, instance);
                }
                for (BakedQuad quad : part.getQuads(null)) buffer.putBakedQuad(pose, quad, instance);
            }
        };
        //?} else {
        /*BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            random.setSeed(state.getSeed(pos));
            quads.addAll(model.getQuads(state, direction, random));
        }
        random.setSeed(state.getSeed(pos));
        quads.addAll(model.getQuads(state, null, random));
        if (quads.isEmpty()) return EMPTY;

        int light = 0xF000F0;
        return (pose, buffer) -> {
            for (BakedQuad quad : quads) emit(pose, buffer, quad, argb, light);
        };
        *///?}
    }

    /** Bakes and emits in one step, for a caller that does not keep the geometry between frames. */
    public static void render(PoseStack.Pose pose, VertexConsumer buffer, BlockState state, BlockPos pos, int argb) {
        bake(state, pos, argb).emit(pose, buffer);
    }

    //? if <26.1 {
    /*/^*
     * Vanilla's own unpack ({@code VertexConsumer.putBulkData}), minus the parts a ghost discards: no
     * per-vertex brightness, no baked colour, no baked light, and the colour's alpha honoured.
     * The packed layout is {@code DefaultVertexFormat.BLOCK}: 8 ints per vertex - x, y, z, colour,
     * u, v, light, normal.
     ^/
    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, int argb, int light) {
        int[] data = quad.getVertices();
        int vertices = data.length / 8;
        Direction direction = quad.getDirection();
        float normalX = direction.getStepX();
        float normalY = direction.getStepY();
        float normalZ = direction.getStepZ();
        for (int i = 0; i < vertices; i++) {
            int base = i * 8;
            float x = Float.intBitsToFloat(data[base]);
            float y = Float.intBitsToFloat(data[base + 1]);
            float z = Float.intBitsToFloat(data[base + 2]);
            float u = Float.intBitsToFloat(data[base + 4]);
            float v = Float.intBitsToFloat(data[base + 5]);
            Vertexes.vertex(buffer, pose, x, y, z, argb, u, v, OverlayTexture.NO_OVERLAY, light,
                    normalX, normalY, normalZ);
        }
    }
    *///?}
}
