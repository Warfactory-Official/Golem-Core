package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jspecify.annotations.Nullable;
//?}

/**
 * A block-entity renderer written once against 26.1's extract/submit shape.
 *
 * <p>Implementations provide three things: a fresh render state, an extract step that copies whatever
 * the renderer needs out of the block entity, and a submit step that turns that state into geometry.
 *
 * <p>On <b>26.1</b> this extends the vanilla interface directly, so implementations <i>are</i> vanilla
 * renderers — no wrapper, no indirection. The default methods below bridge vanilla's signatures to the
 * Golem ones.
 *
 * <p>On <b>older versions</b> vanilla has a single {@code render} method taking the block entity and a
 * {@code MultiBufferSource}, so there is nothing to extend; {@code CompatRenderers} wraps the
 * implementation in an adapter that runs extract and submit back to back. The programming model is
 * identical, but note the extract step runs on the render thread there rather than in a separate pass.
 *
 * <p>Two pieces of 26.1's signature are deliberately dropped, because nothing consumes them: the
 * {@code CrumblingOverlay} break-progress parameter (handled by the base extraction) and the
 * five-argument extract overload. Implementations override the four-argument {@link #extract} instead.
 */
public interface CompatBER<T extends BlockEntity, S extends CompatRenderState>
        //? if >=26.1 {
        extends BlockEntityRenderer<T, S>
        //?}
{

    /** Creates a fresh, empty render state. Called once per renderer instance by the engine. */
    //? if >=26.1 {
    @Override
    //?}
    S createRenderState();

    /**
     * Copies whatever the submit step needs out of the block entity. The base fields
     * ({@code blockPos}, {@code lightCoords}) are already filled in when this runs.
     */
    default void extract(T blockEntity, S state, float partialTicks, Vec3 cameraPosition) {}

    /** Turns the extracted state into geometry. Must not touch the world. */
    void submit(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera);

    /**
     * Maximum distance in blocks at which this renderer draws. Vanilla spells this {@code
     * getViewDistance()} on both eras, but only 26.1's interface is one Golem extends, so consumers
     * override this name and each era's adapter forwards vanilla's call to it.
     */
    default int viewDistance() {
        return 64;
    }

    //? if >=26.1 {
    /** Bridges vanilla's extract signature to {@link #extract}, after running the base extraction. */
    @Override
    default void extractRenderState(
            T blockEntity, S state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        extract(blockEntity, state, partialTicks, cameraPosition);
    }

    /** Bridges vanilla's submit signature to {@link #submit}. */
    @Override
    default void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        submit(state, poseStack, new VanillaSubmitSink(collector),
                new CameraInfo(camera.pos, camera.yRot, camera.orientation));
    }

    /** Forwards vanilla's view-distance hook to {@link #viewDistance}. */
    @Override
    default int getViewDistance() {
        return viewDistance();
    }
    //?}
}
