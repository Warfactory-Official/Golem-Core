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
                    if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                    cursor.set(x, y, z);
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
}
