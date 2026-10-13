package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.CompatEntityRenderState;
import dev.golem.core.render.CompatEntityRenderer;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.gem.Gem.Model;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The entity-side counterpart to {@link GemBER}: an entity renderer that declares its model.
 *
 * <p>An entity's own position and facing move every frame, so nothing here is rigid in the way a
 * bolted-down machine is. The declaration is still a single description: the pose sequence is written as
 * {@link Gem.Part} operations instead of a hand-written submit body, so both backends can read it. Entities are
 * not bound to the instanced backend.
 */
public abstract class GemEntityRenderer<T extends Entity, S extends CompatEntityRenderState>
        extends CompatEntityRenderer<T, S> {

    private @Nullable Model<S> model;

    protected GemEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** States this renderer's model. Runs once, on the render thread, before the first frame. */
    protected abstract void setup(Model<S> model);

    /** The declared model, built on first use for the reason given in {@link GemBER#gem()}. */
    public final Model<S> gem() {
        Model<S> built = model;
        if (built == null) {
            built = Gem.model();
            setup(built);
            built.seal();
            model = built;
        }
        return built;
    }

    @Override
    public final void submit(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {
        Gem.replay(gem(), state, poseStack, sink, state.lightCoords);
        submitUndeclared(state, poseStack, sink, camera);
    }

    /** Geometry this entity builds rather than places. See {@link GemBER#submitUndeclared}. */
    protected void submitUndeclared(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {}

    /** A model declared to draw off-screen is one whose geometry leaves its own bounding box. */
    @Override
    public boolean culled(T entity) {
        return !gem().isOffScreen();
    }
}
