package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.CameraInfo;
import dev.golem.core.render.CompatBER;
import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.gem.Gem.Model;
import dev.golem.core.render.Visuals;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A block-entity renderer that declares its model instead of drawing it.
 *
 * <p>Subclasses implement two things: {@link #setup} states the model once, and {@link #extract} reads
 * the block entity into the render state the model's channels bind to. There is no submit step to
 * write — {@link #submit} is final, and replays the declaration. That is the whole point: a renderer
 * with its own submit body can only feed the vanilla pass, and the instanced backend needs the same
 * model expressed as data it can allocate against before the first frame.
 *
 * <p>The model is built on first use rather than in the constructor. Its parts name loaded meshes, and
 * a subclass field holding one is still null while a superclass constructor runs — calling an
 * overridable setup step from the constructor would hand {@code setup} a half-built renderer. First
 * use is on the render thread, after resources are loaded, and only ever happens once.
 *
 * <p>{@link #viewDistance} and {@link #shouldRenderOffScreen} come from the model rather than from
 * overrides, so a machine states how far it draws in the same place it states what it draws.
 */
public abstract class GemBER<T extends BlockEntity, S extends CompatRenderState> implements CompatBER<T, S> {

    private @Nullable Model<S> model;

    protected GemBER() {}

    /** States this renderer's model. Runs once, on the render thread, before the first frame. */
    protected abstract void setup(Model<S> model);

    /** The declared model, built on first use. */
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
        if (!Visuals.owns(state.blockPos)) {
            Gem.replay(gem(), state, poseStack, sink, state.lightCoords);
        }
        submitUndeclared(state, poseStack, sink, camera);
    }

    /**
     * Geometry a machine <em>builds</em> rather than places: a beam whose vertices come from where it
     * lands, a flame that billboards to the camera, a fluid surface whose shape is its fill level.
     *
     * <p>There is no resident mesh for the instanced backend to allocate one of these against, so this
     * runs through the vanilla pass on every node and in every frame — including a frame where the
     * instanced backend owns the declared tree and the replay above was skipped. Override it only for
     * geometry that genuinely has no fixed shape; a mesh placed by a transform, however the transform is
     * computed, belongs in {@link #setup} where both backends can read it.
     */
    protected void submitUndeclared(S state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {}

    @Override
    public int viewDistance() {
        return gem().viewDistance();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return gem().isOffScreen();
    }

    /**
     * A model that draws outside its own block is never distance-culled here; declaring
     * {@link Gem.Model#offScreen} already put it in the level's globally-rendered set, and culling it
     * again by its one owning cell is what made multiblock machines vanish when that cell scrolled off.
     */
    @Override
    public boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        return gem().isOffScreen()
                || Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPosition, viewDistance());
    }
}
