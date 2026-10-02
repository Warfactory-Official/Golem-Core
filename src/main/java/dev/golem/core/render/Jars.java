package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Draws a {@link WorldInAJar} into the world-effects pass.
 *
 * <p>The jar is handed to the block renderer as its own level, so the cells cull against each other
 * and every model reads its state and tint the way the terrain does. Nothing is cached between
 * frames: the cells are tesselated each time, which is what 1.7 did and what keeps a jar free to be
 * mutated after it is cut.
 *
 * <p>Everything goes into one cutout pass. A jar of terrain is opaque or nearly so, and giving it a
 * layer per block state would cost a submission apiece to sort geometry that is all inside one
 * tumbling cube — 1.7 drew the whole scoop in a single pass with blending off, and this is that.
 */
public final class Jars {

    //? if >=26.1 {
    private static net.minecraft.client.renderer.block.@org.jspecify.annotations.Nullable ModelBlockRenderer blocks;
    private static boolean blocksLit;
    //?}

    private Jars() {}

    /**
     * Submits every filled cell, with the jar's own corner at the pose's origin.
     *
     * <p>Callers centre it themselves — translate by half the jar's size negated, as a falling block
     * translates by {@code (-0.5, 0, -0.5)} — so the pose is free to carry a tumble about the middle.
     */
    public static void submit(WorldInAJar jar, PoseStack pose, int order) {
        if (jar.isEmpty()) return;
        WorldEffects.submit(pose, layer(), order, (last, buffer) -> draw(jar, last, buffer));
    }

    /**
     * Submits every filled cell into {@code sink}, with the jar's own corner at the pose's origin. For a
     * jar drawn somewhere other than the world pass: a scene inside a screen, say.
     */
    public static void submit(WorldInAJar jar, PoseStack pose, SubmitSink sink) {
        if (jar.isEmpty()) return;
        sink.submitCustomGeometry(pose, layer(), (last, buffer) -> draw(jar, last, buffer));
    }

    private static RenderType layer() {
        //? if >=26.1 {
        return RenderTypes.cutoutMovingBlock();
        //?} else {
        /*return RenderType.cutoutMipped();
        *///?}
    }

    //? if >=26.1 {
    /**
     * The block tesselator, kept between frames because building one allocates a lighter. Rebuilt when
     * the ambient-occlusion setting changes, which is the only thing about it that can.
     */
    private static net.minecraft.client.renderer.block.ModelBlockRenderer blocks() {
        boolean lit = Minecraft.getInstance().options.ambientOcclusion().get();
        if (blocks == null || blocksLit != lit) {
            blocks = new net.minecraft.client.renderer.block.ModelBlockRenderer(
                    lit, true, Minecraft.getInstance().getBlockColors());
            blocksLit = lit;
        }
        return blocks;
    }
    //?}

    private static void draw(WorldInAJar jar, PoseStack.Pose last, VertexConsumer buffer) {
        PoseStack pose = new PoseStack();
        pose.last().pose().set(last.pose());
        pose.last().normal().set(last.normal());

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // The fluid tesselator writes section-local positions (pos & 15), which are the jar's own only while
        // the jar fits in one section.
        boolean fluids = jar.sizeX <= 16 && jar.sizeY <= 16 && jar.sizeZ <= 16;
        //? if >=26.1 {
        var models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        var renderer = blocks();
        net.minecraft.client.renderer.block.BlockQuadOutput output = (x, y, z, quad, instance) -> {
            pose.pushPose();
            pose.translate(x, y, z);
            buffer.putBakedQuad(pose.last(), quad, instance);
            pose.popPose();
        };
        //?} else {
        /*var dispatcher = Minecraft.getInstance().getBlockRenderer();
        *///?}

        for (int x = 0; x < jar.sizeX; x++) {
            for (int y = 0; y < jar.sizeY; y++) {
                for (int z = 0; z < jar.sizeZ; z++) {
                    BlockState state = jar.getBlockState(x, y, z);
                    if (state.isAir()) continue;
                    cursor.set(x, y, z);
                    if (fluids && !state.getFluidState().isEmpty()) liquid(jar, cursor, state, last, buffer);
                    if (state.getRenderShape() != RenderShape.MODEL) continue;
                    //? if >=26.1 {
                    pose.pushPose();
                    pose.translate(x, y, z);
                    renderer.tesselateBlock(output, 0F, 0F, 0F, jar, cursor, state,
                            models.get(state), state.getSeed(cursor));
                    pose.popPose();
                    //?} else {
                    /*pose.pushPose();
                    pose.translate(x, y, z);
                    dispatcher.renderBatched(state, cursor, jar, pose, buffer, true,
                            net.minecraft.util.RandomSource.create(state.getSeed(cursor)));
                    pose.popPose();
                    *///?}
                }
            }
        }
    }

    //? if >=26.1 {
    private static net.minecraft.client.renderer.block.@org.jspecify.annotations.Nullable FluidRenderer fluidRenderer;
    private static net.minecraft.client.renderer.block.@org.jspecify.annotations.Nullable FluidStateModelSet fluidModels;
    //?}

    /**
     * A still or flowing fluid in a cell, drawn by the game's own fluid tesselator with the jar as its level
     * so its surface height and hidden faces come out as in the world. The tesselator writes positions
     * relative to its section rather than through a pose, so they are posed here on the way out.
     */
    private static void liquid(WorldInAJar jar, BlockPos pos, BlockState state, PoseStack.Pose pose, VertexConsumer buffer) {
        VertexConsumer posed = new PosedConsumer(buffer, pose);
        //? if >=26.1 {
        var models = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        if (fluidRenderer == null || fluidModels != models) {
            fluidRenderer = new net.minecraft.client.renderer.block.FluidRenderer(models);
            fluidModels = models;
        }
        fluidRenderer.tesselate(jar, pos, layer -> posed, state, state.getFluidState());
        //?} else {
        /*Minecraft.getInstance().getBlockRenderer().renderLiquid(pos, jar, posed, state, state.getFluidState());
        *///?}
    }

    /** Passes vertices through, moving positions and normals by a pose. */
    private static final class PosedConsumer implements VertexConsumer {

        private final VertexConsumer out;
        private final org.joml.Matrix4f pose;
        private final org.joml.Matrix3f normal;
        private final org.joml.Vector3f scratch = new org.joml.Vector3f();

        PosedConsumer(VertexConsumer out, PoseStack.Pose pose) {
            this.out = out;
            this.pose = new org.joml.Matrix4f(pose.pose());
            this.normal = new org.joml.Matrix3f(pose.normal());
        }

        //? if >=1.21 {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            pose.transformPosition(x, y, z, scratch);
            out.addVertex(scratch.x, scratch.y, scratch.z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            out.setColor(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            out.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            out.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            out.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            normal.transform(x, y, z, scratch).normalize();
            out.setNormal(scratch.x, scratch.y, scratch.z);
            return this;
        }
        //?} else {
        /*@Override
        public VertexConsumer vertex(double x, double y, double z) {
            pose.transformPosition((float) x, (float) y, (float) z, scratch);
            out.vertex(scratch.x, scratch.y, scratch.z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            out.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            out.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            out.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            out.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            normal.transform(x, y, z, scratch).normalize();
            out.normal(scratch.x, scratch.y, scratch.z);
            return this;
        }

        @Override
        public void endVertex() {
            out.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            out.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            out.unsetDefaultColor();
        }
        *///?}

        //? if >=26.1 {
        @Override
        public VertexConsumer setColor(int color) {
            out.setColor(color);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            out.setLineWidth(width);
            return this;
        }
        //?}
    }
}
