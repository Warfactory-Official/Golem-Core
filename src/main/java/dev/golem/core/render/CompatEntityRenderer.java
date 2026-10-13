package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;

//? if >=26.1 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
*///?}

/**
 * An entity renderer written once against 26.1's extract/submit shape, on every version.
 *
 * <p>The entity-side counterpart to {@link CompatBER}. 26.1 renders in two passes: {@code extractRenderState}
 * snapshots what the frame needs and {@code submit} hands geometry to a collector the engine draws later. Older
 * versions have a single {@code render} that draws immediately into a {@code MultiBufferSource}. Subclasses
 * implement the two-pass form; below 26.1 this class runs both halves back to back and replays the submissions
 * through {@link RecordingSubmitSink}, which keeps {@link SubmitSink#order}.
 *
 * <p>Unlike {@code CompatBER} this is a class, because it must be a vanilla {@code EntityRenderer}: the
 * registries take the concrete type, and its generic arity differs between the eras, so the {@code extends}
 * clause is version-split.
 *
 * @param <T> the entity type
 * @param <S> its render state
 */
public abstract class CompatEntityRenderer<T extends Entity, S extends CompatEntityRenderState>
        //? if >=26.1 {
        extends EntityRenderer<T, S>
        //?} else {
        /*extends EntityRenderer<T>
        *///?}
{

    protected CompatEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** Creates the per-frame state object. Called by the engine on 26.1, and by this class below it. */
    //? if >=26.1 {
    @Override
    //?}
    public abstract S createRenderState();

    /** Snapshots what {@link #submit} will need. Runs once per frame, before submission. */
    public abstract void extract(T entity, S state, float partialTicks);

    /** Hands this entity's geometry to the sink. */
    public abstract void submit(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera);

    /**
     * Whether the entity's bounding box should be frustum-culled. An entity whose renderer draws far
     * outside that box (a mushroom cloud, say) must answer false or it vanishes at the screen edge.
     */
    public boolean culled(T entity) {
        return true;
    }

    //? if >=26.1 {
    /** Bridges vanilla's extract signature to {@link #extract}. */
    @Override
    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        extract(entity, state, partialTicks);
    }

    /** Bridges vanilla's submit signature to {@link #submit}. */
    @Override
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        submit(state, poseStack, new VanillaSubmitSink(collector, camera),
                new CameraInfo(camera.pos, camera.yRot, camera.orientation));
    }

    /** Forwards vanilla's culling opt-out to {@link #culled}. */
    @Override
    protected boolean affectedByCulling(T entity) {
        return culled(entity);
    }
    //?} else {
    /*/^*
     * Vanilla's single-pass entry point. Runs both halves of the 26.1 shape in order and flushes the
     * recorded submissions into the buffer source the engine handed us.
     *
     * <p>The camera is rebuilt from the render dispatcher rather than passed in, because this era gives
     * a renderer no camera argument — the dispatcher is where vanilla itself keeps it.
     ^/
    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        S state = createRenderState();
        // 26.1's vanilla extraction fills this before the subclass sees the state; do the same here so
        // a renderer can read state.lightCoords on either era.
        state.lightCoords = packedLight;
        extract(entity, state, partialTicks);

        net.minecraft.client.Camera camera = this.entityRenderDispatcher.camera;
        CameraInfo info = camera == null
                ? new CameraInfo(Vec3.ZERO, 0F, new org.joml.Quaternionf())
                : new CameraInfo(camera.getPosition(), camera.getYRot(), camera.rotation());

        RecordingSubmitSink sink = new RecordingSubmitSink();
        submit(state, poseStack, sink, info);
        sink.flush(bufferSource, entity.level());
    }

    /^*
     * This era culls through {@code shouldRender}; {@link #culled} answering false means "always draw",
     * which is what 26.1's affectedByCulling opt-out amounts to.
     ^/
    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return !culled(entity) || super.shouldRender(entity, frustum, camX, camY, camZ);
    }

    /^*
     * Required by this era's abstract contract and unused by these renderers, which bind their own
     * textures through render types. 26.1 dropped it entirely.
     ^/
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
    }
    *///?}
}
