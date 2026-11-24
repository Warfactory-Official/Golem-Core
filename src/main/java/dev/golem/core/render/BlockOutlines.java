package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.golem.core.client.Frames;
import dev.golem.core.hook.Hook;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla's hovered-block outline, skipped while a condition holds (1.7's cancelled {@code DrawBlockHighlightEvent})
 * or drawn in vanilla's colour, width and high-contrast pass around another shape a listener answers. Client only.
 */
public final class BlockOutlines {

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<BooleanSupplier> SUPPRESSORS = Hook.notifying();
    /** Replacement outlines; the first non-null answer is drawn. */
    public static final Hook<Shape> SHAPES = Hook.notifying();
    private static final int COLOR = 0x66000000;
    private static final int HIGH_CONTRAST_COLOR = 0xFF57FFE1;
    private static boolean registered;

    private BlockOutlines() {}

    @FunctionalInterface
    public interface Shape {
        /** The outline of the hovered block at {@code pos}, in that block's frame, or null to keep its own. */
        @Nullable VoxelShape outline(Level level, BlockPos pos, BlockState state);
    }

    /** Skips vanilla's block outline on every frame {@code condition} answers true. */
    public static void suppressWhen(BooleanSupplier condition) {
        SUPPRESSORS.register(condition);
        register();
    }

    /** Outlines whatever {@code shape} answers for the hovered block instead of the block's own shape. */
    public static void replaceShape(Shape shape) {
        SHAPES.register(shape);
        register();
    }

    /** The outline drawn instead of the block's own at {@code pos}, or null where vanilla's stands. */
    public static @Nullable VoxelShape replacement(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !level.getWorldBorder().isWithinBounds(pos)) return null;
        for (Shape listener : SHAPES) {
            VoxelShape shape = listener.outline(level, pos, state);
            if (shape != null) return shape;
        }
        return null;
    }

    private static boolean suppressed() {
        for (BooleanSupplier condition : SUPPRESSORS) if (condition.getAsBoolean()) return true;
        return false;
    }

    /** Where an outline's lines go: a buffer source, or a 26.2 submit collector. */
    private interface Lines {
        void draw(PoseStack pose, RenderType type, Edges edges);
    }

    private interface Edges {
        void draw(PoseStack.Pose pose, VertexConsumer buffer);
    }

    //? if >=26.2 {
    /*private static Lines lines(net.minecraft.client.renderer.SubmitNodeCollector collector) {
        return (pose, type, edges) -> collector.submitCustomGeometry(pose, type, edges::draw);
    }
    *///?} else {
    private static Lines lines(MultiBufferSource buffers) {
        return (pose, type, edges) -> edges.draw(pose.last(), buffers.getBuffer(type));
    }
    //?}

    private static boolean drawReplacement(Lines buffers, PoseStack pose, BlockPos pos, Vec3 camera,
                                           boolean highContrast) {
        Level level = Minecraft.getInstance().level;
        VoxelShape shape = level == null ? null : replacement(level, pos);
        if (shape == null) return false;
        drawVanilla(buffers, pose, level, pos, camera, shape, highContrast);
        return true;
    }

    private static void drawVanilla(Lines buffers, PoseStack pose, Level level, BlockPos pos, Vec3 camera,
                                    VoxelShape shape, boolean highContrast) {
        //? if >=1.21.11 {
        if (highContrast) {
            draw(pose, buffers, net.minecraft.client.renderer.rendertype.RenderTypes.secondaryBlockOutline(),
                    level, pos, camera, shape, 0xFF000000, 7F);
        }
        //? if >=26.1 {
        float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
        //?} else {
        /*float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        *///?}
        //?} else {
        /*float width = 0F; // the render type carries the width before 26.1
        *///?}
        draw(pose, buffers, CompatRenderTypes.lines(), level, pos, camera, shape,
                highContrast ? HIGH_CONTRAST_COLOR : COLOR, width);
    }

    private static void draw(PoseStack pose, Lines buffers, RenderType type, Level level, BlockPos pos, Vec3 camera,
                             VoxelShape shape, int color, float width) {
        pose.pushPose();
        SubLevelPoses.around(pose, level, pos, camera, Frames.partialTick());
        buffers.draw(pose, type, (last, buffer) -> shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1), dy = (float) (y2 - y1), dz = (float) (z2 - z1);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            float nx = dx / length, ny = dy / length, nz = dz / length;
            Vertexes.line(buffer, last, (float) x1, (float) y1, (float) z1, color, nx, ny, nz, width);
            Vertexes.line(buffer, last, (float) x2, (float) y2, (float) z2, color, nx, ny, nz, width);
        }));
        pose.popPose();
    }

    private static void register() {
        if (registered) return;
        registered = true;
        //? if neoforge && >=1.21.11 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent event) -> {
                    if (suppressed()) {
                        event.setCanceled(true);
                        return;
                    }
                    VoxelShape shape = replacement(event.getLevel(), event.getBlockPos());
                    if (shape == null) return;
                    BlockPos pos = event.getBlockPos().immutable();
                    //? if >=26.2 {
                    /*event.addCustomRenderer((state, collector, pose, levelState) -> {
                        Level level = Minecraft.getInstance().level;
                        if (level == null) return false;
                        drawVanilla(lines(collector), pose, level, pos, levelState.cameraRenderState.pos, shape,
                                state.highContrast());
                        return true;
                    });
                    *///?} else {
                    event.addCustomRenderer((state, buffers, pose, translucentPass, levelState) -> {
                        Level level = Minecraft.getInstance().level;
                        if (level == null) return false;
                        if (state.isTranslucent() == translucentPass) {
                            drawVanilla(lines(buffers), pose, level, pos, levelState.cameraRenderState.pos, shape,
                                    state.highContrast());
                            buffers.endLastBatch();
                        }
                        return true;
                    });
                    //?}
                });
        //?} elif fabric && >=26.2 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, outline) -> !suppressed() && !drawReplacement(lines(context.submitNodeCollector()),
                        context.poseStack(), outline.pos(), context.levelState().cameraRenderState.pos,
                        outline.highContrast()));
        *///?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, outline) -> !suppressed() && !drawReplacement(lines(context.bufferSource()),
                        context.poseStack(), outline.pos(), context.levelState().cameraRenderState.pos,
                        outline.highContrast()));
        *///?} elif neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) -> {
                    if (suppressed() || drawReplacement(lines(event.getMultiBufferSource()), event.getPoseStack(),
                            event.getTarget().getBlockPos(), event.getCamera().getPosition(), false)) {
                        event.setCanceled(true);
                    }
                });
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderHighlightEvent.Block event) -> {
                    if (suppressed() || drawReplacement(lines(event.getMultiBufferSource()), event.getPoseStack(),
                            event.getTarget().getBlockPos(), event.getCamera().getPosition(), false)) {
                        event.setCanceled(true);
                    }
                });
        *///?} elif fabric && >=1.21.11 {
        /*net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, outline) -> !suppressed() && !drawReplacement(lines(context.consumers()),
                        context.matrices(), outline.pos(), context.worldState().cameraRenderState.pos,
                        outline.highContrast()));
        *///?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, hit) -> {
                    if (suppressed()) return false;
                    if (!context.blockOutlines() || context.consumers() == null
                            || !(hit instanceof net.minecraft.world.phys.BlockHitResult block)
                            || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return true;
                    return !drawReplacement(lines(context.consumers()), context.matrixStack(), block.getBlockPos(),
                            context.camera().getPosition(), false);
                });
        *///?}
    }
}
