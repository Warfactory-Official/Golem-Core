package dev.golem.core.render.living;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.SubmitSink;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.world.entity.Mob;

//? if >=26.1 {
import dev.golem.core.render.VanillaSubmitSink;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
//?} else {
/*import dev.golem.core.render.RecordingSubmitSink;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
*///?}

/**
 * A mob renderer written once against 26.1's extract/submit shape, on every version. It is the living
 * counterpart to {@link dev.golem.core.render.CompatEntityRenderer} and a separate class because vanilla's
 * {@code MobRenderer} gives a mob its body yaw, hurt flash, death tilt, name tag and layers, and its generic
 * arity differs between the eras.
 *
 * <p>Subclasses implement the 26.1 hooks ({@link #createRenderState}, {@link #extract}, {@link #texture},
 * {@link #scale}, {@link #submitExtra}) and the facade binds them to each era's vanilla hook. Below 26.1,
 * vanilla's single {@code render} is wrapped: the state is created and filled from the entity first, vanilla's
 * pass runs against it, and extra geometry is replayed afterwards through a {@code RecordingSubmitSink}.
 *
 * @param <T> the mob
 * @param <S> its render state
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class CompatMobRenderer<T extends Mob, S extends CompatLivingRenderState>
        //? if >=26.1 {
        extends MobRenderer<T, S, EntityModel<? super S>>
        //?} else {
        /*extends MobRenderer<T, EntityModel<T>>
        *///?}
{

    //? if <26.1 {
    /*private @Nullable CompatEntityModel<S> compat;
    private @Nullable S current;
    *///?}

    protected CompatMobRenderer(EntityRendererProvider.Context context, LivingModel<S> model, float shadow) {
        //? if >=26.1 {
        super(context, model.model, shadow);
        //?} else {
        /*super(context, (EntityModel<T>) model.model, shadow);
        this.compat = model.compat;
        *///?}
    }

    /** Creates the per-frame state object. Called by the engine on 26.1, and by this class below it. */
    //? if >=26.1 {
    @Override
    //?}
    public abstract S createRenderState();

    /** Snapshots what this frame needs beyond what vanilla extracts. */
    protected void extract(T entity, S state, float partialTicks) {}

    /** The skin drawn this frame. */
    protected abstract
    //? if >=26.1 {
    Identifier
    //?} else {
    /*ResourceLocation
    *///?}
    texture(S state);

    /** Extra geometry drawn in world space at the entity's origin, after the body — beams, arcs. */
    protected void submitExtra(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {}

    /** Degrees the body tips over when dead. Vanilla's 90. */
    protected float flipDegrees() {
        return 90F;
    }

    /** Whether the bounding box is frustum-culled; a renderer that draws far outside it answers false. */
    public boolean culled(T entity) {
        return true;
    }

    /** Switches the body drawn from now on — a variant chosen per frame. */
    protected final void useModel(LivingModel<S> model) {
        //? if >=26.1 {
        this.model = model.model;
        //?} else {
        /*this.model = (EntityModel<T>) model.model;
        this.compat = model.compat;
        *///?}
    }

    //? if >=26.1 {
    /** A scale applied before the body draws. Vanilla's own hook, restated so it is documented here. */
    @Override
    protected void scale(S state, PoseStack poseStack) {}

    @Override
    public void extractRenderState(T entity, S state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.attackTime = entity.getAttackAnim(partialTicks);
        extract(entity, state, partialTicks);
    }

    @Override
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        submitExtra(state, poseStack, new VanillaSubmitSink(collector, camera),
                new CameraInfo(camera.pos, camera.yRot, camera.orientation));
    }

    @Override
    public Identifier getTextureLocation(S state) {
        return texture(state);
    }

    @Override
    protected float getFlipDegrees() {
        return flipDegrees();
    }

    @Override
    protected boolean affectedByCulling(T entity) {
        return culled(entity);
    }
    //?} else {
    /*/^* A scale applied before the body draws; the same hook 26.1 calls with a state. ^/
    protected void scale(S state, PoseStack poseStack) {}

    /^*
     * The state of the entity being drawn right now, for hooks vanilla calls with the entity instead.
     * Outside a pass (a layer asking for the skin early, say) a fresh state is filled on the spot.
     ^/
    protected final S current(T entity) {
        S state = current;
        if (state != null) return state;
        state = createRenderState();
        fill(entity, state, 0F);
        extract(entity, state, 0F);
        return state;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        S state = createRenderState();
        state.lightCoords = packedLight;
        fill(entity, state, partialTicks);
        extract(entity, state, partialTicks);
        current = state;
        if (compat != null) compat.bound = state;
        try {
            super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
            net.minecraft.client.Camera camera = this.entityRenderDispatcher.camera;
            CameraInfo info = camera == null
                    ? new CameraInfo(Vec3.ZERO, 0F, new org.joml.Quaternionf())
                    : new CameraInfo(camera.getPosition(), camera.getYRot(), camera.rotation());
            RecordingSubmitSink sink = new RecordingSubmitSink();
            submitExtra(state, poseStack, sink, info);
            sink.flush(bufferSource, entity.level());
        } finally {
            current = null;
            if (compat != null) compat.bound = null;
        }
    }

    /^* The fields 26.1's LivingEntityRenderer extracts, computed the way vanilla's render computes them. ^/
    private void fill(T entity, S state, float partialTicks) {
        state.ageInTicks = entity.tickCount + partialTicks;
        state.bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headRot = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        state.yRot = Mth.wrapDegrees(headRot - state.bodyRot);
        state.xRot = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        state.deathTime = entity.deathTime > 0 ? entity.deathTime + partialTicks : 0F;
        state.walkAnimationPos = entity.walkAnimation.position(partialTicks);
        state.walkAnimationSpeed = Math.min(entity.walkAnimation.speed(partialTicks), 1F);
        if (entity.isBaby()) state.walkAnimationPos *= 3F;
        state.attackTime = entity.getAttackAnim(partialTicks);
        state.isBaby = entity.isBaby();
        state.isInvisible = entity.isInvisible();
        state.hasRedOverlay = entity.hurtTime > 0 || entity.deathTime > 0;
        state.isFullyFrozen = entity.isFullyFrozen();
        //? if >=1.20.5 {
        state.scale = entity.getScale();
        //?} else {
        /^state.scale = 1F;^/
        //?}
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTicks) {
        scale(current(entity), poseStack);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture(current(entity));
    }

    @Override
    protected float getFlipDegrees(T entity) {
        return flipDegrees();
    }

    @Override
    public boolean shouldRender(T entity, Frustum frustum, double camX, double camY, double camZ) {
        return !culled(entity) || super.shouldRender(entity, frustum, camX, camY, camZ);
    }
    *///?}
}
