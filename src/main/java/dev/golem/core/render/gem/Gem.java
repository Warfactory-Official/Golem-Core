package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.golem.core.render.Poses;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.anim.BusAnimationSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * A model a renderer declares instead of drawing.
 *
 * <p>A renderer that writes its own {@code submit} can only ever feed the vanilla pass: the geometry
 * exists for the length of one call and the transforms live in a {@code PoseStack} that is gone by the
 * next frame. The instanced backend needs the opposite — every bucket of (mesh, surface) known before
 * the first frame, rigid transforms written once, and only the moving ones touched again. Those two
 * demands cannot both be met by imperative drawing code, so the renderer states its model once and
 * each backend reads that same statement.
 *
 * <p>A model is a tree of {@linkplain Part parts}. Each part carries an ordered list of transform
 * operations and, usually, one lump of {@link Geometry} drawn with one {@link Surface}; a part with no
 * geometry is a joint that exists only to place its children. <b>Operations apply in declaration
 * order, and that order is exactly a {@code PoseStack}'s call order</b> — {@code translate} then
 * {@code rotate} here does what {@code poseStack.translate} then {@code poseStack.mulPose} did — so
 * transcribing an existing renderer is mechanical and the result is comparable line by line.
 *
 * <p>An operation is either fixed or read from the render state. A part with no state-driven operation
 * is <em>rigid</em>, and a rigid part under rigid parents never has its transform recomputed; that is
 * the whole reason a static machine is cheap to instance. Animation is declared three ways, because
 * this mod's animations arrive in three shapes:
 *
 * <ul>
 *   <li>{@link Part#angle} / {@link Part#slide} / {@link Part#spin} bind one float from the render
 *       state straight to one axis. Most machines animate on exactly one float.</li>
 *   <li>{@link Part#keys} plays a {@link BusAnimationSequence} whose playhead is driven by the render
 *       state rather than by the clock — which is what lets a keyframed pose describe a machine whose
 *       progress is a number rather than an elapsed time.</li>
 *   <li>Genuinely procedural motion — an arm that picks random targets and speed-limits toward them —
 *       binds its joint angles through {@link Part#angle} and stays procedural. Nothing here tries to
 *       turn it into keyframes.</li>
 * </ul>
 */
public final class Gem {

    /** A rotation or translation axis, named without borrowing a version-specific type. */
    public enum Axes { X, Y, Z }

    /** Reads one float out of a render state. */
    @FunctionalInterface
    public interface FloatOf<S> {
        float of(S state);
    }

    /** Reads one packed int (a light or an ARGB tint) out of a render state. */
    @FunctionalInterface
    public interface IntOf<S> {
        int of(S state);
    }

    /** Answers a yes/no question about a render state. */
    @FunctionalInterface
    public interface Test<S> {
        boolean of(S state);
    }

    /** Chooses one of a part's skins from a render state. */
    @FunctionalInterface
    public interface SurfaceOf<S> {
        Surface of(S state);
    }

    /** Chooses one of a part's meshes from a render state. */
    @FunctionalInterface
    public interface GeometryOf<S> {
        Geometry of(S state);
    }

    /** Reads one already-composed transform out of a render state. */
    @FunctionalInterface
    /** Supplies a whole animation-bus transform from the render state. */
    public interface BusOf<S> {
        double[] of(S state);
    }

    public interface PoseOf<S> {
        Matrix4fc of(S state);
    }

    private Gem() {}

    /** Starts an empty model. A renderer builds one in its setup step and never rebuilds it. */
    public static <S> Model<S> model() {
        return new Model<>();
    }

    /**
     * One step of a part's local transform.
     *
     * <p>Both halves describe the same step: the matrix form is what the instanced backend and the
     * tests evaluate, the pose-stack form is what the vanilla pass replays. They are written as a pair
     * rather than deriving one from the other because {@code PoseStack} also maintains a normal matrix,
     * and the two eras do not agree on how a raw matrix multiplied into it updates that. Keeping the
     * vanilla path on real {@code PoseStack} calls means its output is unchanged from the hand-written
     * renderer it replaces; {@code GemOpAgreementTest} holds the pair honest.
     */
    interface Op<S> {
        void apply(Matrix4f matrix, S state);

        void apply(PoseStack stack, S state);

        boolean dynamic();
    }

    private static float radians(float degrees) {
        return degrees * ((float) Math.PI / 180F);
    }

    private static void rotate(Matrix4f matrix, Axes axis, float degrees) {
        switch (axis) {
            case X -> matrix.rotateX(radians(degrees));
            case Y -> matrix.rotateY(radians(degrees));
            case Z -> matrix.rotateZ(radians(degrees));
        }
    }

    private static void rotate(PoseStack stack, Axes axis, float degrees) {
        switch (axis) {
            case X -> stack.mulPose(Axis.XP.rotationDegrees(degrees));
            case Y -> stack.mulPose(Axis.YP.rotationDegrees(degrees));
            case Z -> stack.mulPose(Axis.ZP.rotationDegrees(degrees));
        }
    }

    private record Translate<S>(float x, float y, float z) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) { matrix.translate(x, y, z); }
        @Override public void apply(PoseStack stack, S state) { stack.translate(x, y, z); }
        @Override public boolean dynamic() { return false; }
    }

    private record Slide<S>(Axes axis, FloatOf<S> amount) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) {
            float d = amount.of(state);
            matrix.translate(axis == Axes.X ? d : 0F, axis == Axes.Y ? d : 0F, axis == Axes.Z ? d : 0F);
        }
        @Override public void apply(PoseStack stack, S state) {
            float d = amount.of(state);
            stack.translate(axis == Axes.X ? d : 0F, axis == Axes.Y ? d : 0F, axis == Axes.Z ? d : 0F);
        }
        @Override public boolean dynamic() { return true; }
    }

    private record Rotate<S>(Axes axis, float degrees) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) { rotate(matrix, axis, degrees); }
        @Override public void apply(PoseStack stack, S state) { rotate(stack, axis, degrees); }
        @Override public boolean dynamic() { return false; }
    }

    private record Angle<S>(Axes axis, FloatOf<S> degrees) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) { rotate(matrix, axis, degrees.of(state)); }
        @Override public void apply(PoseStack stack, S state) { rotate(stack, axis, degrees.of(state)); }
        @Override public boolean dynamic() { return true; }
    }

    private record Posed<S>(PoseOf<S> pose) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) { matrix.mul(pose.of(state)); }
        @Override public void apply(PoseStack stack, S state) { Poses.mulPose(stack, new Matrix4f(pose.of(state))); }
        @Override public boolean dynamic() { return true; }
    }

    private record Scale<S>(float x, float y, float z) implements Op<S> {
        @Override public void apply(Matrix4f matrix, S state) { matrix.scale(x, y, z); }
        @Override public void apply(PoseStack stack, S state) { stack.scale(x, y, z); }
        @Override public boolean dynamic() { return false; }
    }

    private record Stretch<S>(@Nullable Axes axis, FloatOf<S> factor) implements Op<S> {

        private float x(float f) { return axis == null || axis == Axes.X ? f : 1F; }

        private float y(float f) { return axis == null || axis == Axes.Y ? f : 1F; }

        private float z(float f) { return axis == null || axis == Axes.Z ? f : 1F; }

        @Override public void apply(Matrix4f matrix, S state) {
            float f = factor.of(state);
            matrix.scale(x(f), y(f), z(f));
        }

        @Override public void apply(PoseStack stack, S state) {
            float f = factor.of(state);
            stack.scale(x(f), y(f), z(f));
        }

        @Override public boolean dynamic() { return true; }
    }

    /**
     * Plays one animation bus, with the render state supplying the playhead in milliseconds.
     *
     * <p>The order below is the one the weapon renderers already apply to a bus — translate, the three
     * rotations in the bus's own axis order, the pivot subtracted, then scale — and it is reproduced
     * here rather than reinvented so a machine and a gun read the same keyframe table identically.
     */
    private static void applyBus(Matrix4f matrix, double[] t) {
        matrix.translate((float) t[0], (float) t[1], (float) t[2]);
        for (int i = 0; i < 3; i++) {
            int axis = (int) t[12 + i];
            rotate(matrix, Axes.values()[axis], (float) t[3 + axis]);
        }
        matrix.translate((float) -t[9], (float) -t[10], (float) -t[11]);
        matrix.scale((float) t[6], (float) t[7], (float) t[8]);
    }

    private static void applyBus(PoseStack stack, double[] t) {
        stack.translate(t[0], t[1], t[2]);
        for (int i = 0; i < 3; i++) {
            int axis = (int) t[12 + i];
            rotate(stack, Axes.values()[axis], (float) t[3 + axis]);
        }
        stack.translate(-t[9], -t[10], -t[11]);
        stack.scale((float) t[6], (float) t[7], (float) t[8]);
    }

    private record Keys<S>(BusAnimationSequence sequence, FloatOf<S> playhead) implements Op<S> {

        private double[] at(S state) {
            return sequence.getTransformation(Math.round(playhead.of(state)));
        }

        @Override public void apply(Matrix4f matrix, S state) { applyBus(matrix, at(state)); }

        @Override public void apply(PoseStack stack, S state) { applyBus(stack, at(state)); }

        @Override public boolean dynamic() { return true; }
    }

    /** The same transform, read live from the state rather than sampled out of a keyframe table. */
    private record Bussed<S>(BusOf<S> bus) implements Op<S> {

        @Override public void apply(Matrix4f matrix, S state) { applyBus(matrix, bus.of(state)); }

        @Override public void apply(PoseStack stack, S state) { applyBus(stack, bus.of(state)); }

        @Override public boolean dynamic() { return true; }
    }

    /** A whole declared model: its parts, and the frame-wide answers a renderer would otherwise override. */
    public static final class Model<S> {

        private final List<Part<S>> roots = new ArrayList<>();
        private int viewDistance = 64;
        private boolean offScreen;
        private @Nullable IntOf<S> light;
        private @Nullable IntOf<S> overlay;
        private boolean sealed;

        private Model() {}

        /** Adds a top-level part. */
        public Part<S> part(Geometry geometry, Surface surface) {
            return add(new Part<>(Objects.requireNonNull(geometry, "geometry"),
                    Objects.requireNonNull(surface, "surface")));
        }

        /** Adds a top-level joint: no geometry of its own, only a transform its children inherit. */
        public Part<S> joint() {
            return add(new Part<>(null, null));
        }

        private Part<S> add(Part<S> part) {
            requireOpen();
            roots.add(part);
            return part;
        }

        /** How far away this model still draws, in blocks. */
        public Model<S> viewDistance(int blocks) {
            requireOpen();
            this.viewDistance = blocks;
            return this;
        }

        /**
         * Declares that this model draws outside its own block, so it must not be culled with it. An
         * OBJ machine spanning a multiblock footprint always needs this; its geometry reaches several
         * blocks past the one cell that owns the renderer.
         */
        public Model<S> offScreen() {
            requireOpen();
            this.offScreen = true;
            return this;
        }

        /** The light every part uses unless it overrides it. Defaults to the light the engine sampled. */
        public Model<S> light(IntOf<S> light) {
            requireOpen();
            this.light = Objects.requireNonNull(light, "light");
            return this;
        }

        /**
         * The overlay texel every part samples. Defaults to none.
         *
         * <p>A block entity and a free entity are both drawn without one, so this exists for the one
         * caller that is handed a real overlay and must pass it on: an item model. Vanilla gives an item
         * the hurt flash of whoever is holding it, and dropping it would leave a machine's icon the only
         * thing in a struck mob's hand that does not redden.
         */
        public Model<S> overlay(IntOf<S> overlay) {
            requireOpen();
            this.overlay = Objects.requireNonNull(overlay, "overlay");
            return this;
        }

        public List<Part<S>> roots() {
            return List.copyOf(roots);
        }

        public int viewDistance() {
            return viewDistance;
        }

        public boolean isOffScreen() {
            return offScreen;
        }

        /** The model-wide light, falling back to the light the engine sampled. */
        public int lightOf(S state, int fallback) {
            return light == null ? fallback : light.of(state);
        }

        /** The model-wide overlay texel, which is none unless the model named one. */
        public int overlayOf(S state) {
            return overlay == null ? OverlayTexture.NO_OVERLAY : overlay.of(state);
        }

        /**
         * Whether nothing anywhere in the model reads the render state.
         *
         * <p>The model's own channels count. A tree of fixed parts under a model that chooses its
         * overlay from the state is not rigid: the instanced backend writes a rigid model once and then
         * leaves it alone, so a chooser above the parts would be sampled on the first frame and never
         * again. Light is the exception it looks like but is not — a light change re-writes the visual
         * through {@code updateLight} rather than through the rigidity check.
         */
        public boolean rigid() {
            if (overlay != null) return false;
            for (Part<S> root : roots) if (!root.rigidTree()) return false;
            return true;
        }

        /**
         * Closes the model to further declaration. A renderer's setup step runs once and the instanced
         * backend allocates against the tree it produced, so a part added later would exist on the
         * vanilla path and be invisible on the instanced one.
         *
         * <p>A model with no parts at all is allowed: a machine whose whole appearance is built rather
         * than placed — a pool of melt, a beam — has nothing to declare and draws entirely through
         * {@code submitUndeclared}.
         */
        public Model<S> seal() {
            sealed = true;
            for (Part<S> root : roots) root.seal();
            return this;
        }

        private void requireOpen() {
            if (sealed) throw new IllegalStateException("A gem model cannot be changed after it is sealed");
        }
    }

    /** One node of a model: a transform, optionally some geometry, and any children riding on it. */
    public static final class Part<S> {

        private final @Nullable Geometry geometry;
        private final @Nullable Surface surface;
        private final List<Op<S>> ops = new ArrayList<>();
        private final List<Part<S>> children = new ArrayList<>();
        private @Nullable Test<S> visible;
        private @Nullable GeometryOf<S> mesh;
        private @Nullable SurfaceOf<S> skin;
        private @Nullable IntOf<S> tint;
        private @Nullable IntOf<S> light;
        private int order;
        private boolean dynamic;
        private boolean sealed;

        private Part(@Nullable Geometry geometry, @Nullable Surface surface) {
            this.geometry = geometry;
            this.surface = surface;
        }

        /** Adds a child part, which inherits this part's transform. */
        public Part<S> child(Geometry geometry, Surface surface) {
            return addChild(new Part<>(Objects.requireNonNull(geometry, "geometry"),
                    Objects.requireNonNull(surface, "surface")));
        }

        /** Adds a child joint: a transform its own children inherit, drawing nothing itself. */
        public Part<S> joint() {
            return addChild(new Part<>(null, null));
        }

        private Part<S> addChild(Part<S> child) {
            requireOpen();
            children.add(child);
            return child;
        }

        public Part<S> translate(float x, float y, float z) {
            return op(new Translate<>(x, y, z));
        }

        public Part<S> rotate(Axes axis, float degrees) {
            return op(new Rotate<>(axis, degrees));
        }

        public Part<S> scale(float scale) {
            return scale(scale, scale, scale);
        }

        public Part<S> scale(float x, float y, float z) {
            return op(new Scale<>(x, y, z));
        }

        /** Translates along one axis by a distance the render state supplies. */
        public Part<S> slide(Axes axis, FloatOf<S> blocks) {
            return op(new Slide<>(axis, Objects.requireNonNull(blocks, "blocks")));
        }

        /**
         * Scales along one axis by a factor the render state supplies — a tank's contents standing as
         * tall as they are full, a beam as long as the distance it crosses.
         */
        public Part<S> stretch(Axes axis, FloatOf<S> factor) {
            return op(new Stretch<>(Objects.requireNonNull(axis, "axis"),
                    Objects.requireNonNull(factor, "factor")));
        }

        /** Scales all three axes together by a factor the render state supplies. */
        public Part<S> stretch(FloatOf<S> factor) {
            return op(new Stretch<>(null, Objects.requireNonNull(factor, "factor")));
        }

        /** Rotates about one axis through this part's own origin, by degrees the render state supplies. */
        public Part<S> angle(Axes axis, FloatOf<S> degrees) {
            return op(new Angle<>(axis, Objects.requireNonNull(degrees, "degrees")));
        }

        /**
         * Rotates about one axis through a pivot other than this part's origin — the shape a spinning
         * fan or rotor mounted away from the model centre needs.
         */
        public Part<S> spin(Axes axis, float pivotX, float pivotY, float pivotZ, FloatOf<S> degrees) {
            return translate(pivotX, pivotY, pivotZ)
                    .angle(axis, degrees)
                    .translate(-pivotX, -pivotY, -pivotZ);
        }

        /**
         * Applies a transform the render state has already composed — for a renderer that builds its
         * bones as matrices rather than as a sequence of named steps.
         *
         * <p>This is the one op whose two forms are not the same call twice: the pose-stack side
         * multiplies a raw matrix in, which updates the stack's normal matrix by its own rule rather
         * than by the axis-by-axis one the other ops use. It is here because a renderer that already
         * hands out matrices was drawing exactly this way before it was declared, and matching it keeps
         * the vanilla output unchanged.
         */
        public Part<S> pose(PoseOf<S> pose) {
            return op(new Posed<>(Objects.requireNonNull(pose, "pose")));
        }

        /**
         * Applies one animation bus the render state hands over whole.
         *
         * <p>{@link #keys} samples a bus out of a keyframe table; this takes one that is already live,
         * which is what a held weapon's joints read. The transform is the same either way, down to the
         * rotations being applied in the order the bus itself names — the axis order is data, not a
         * fixed X-Y-Z, which is exactly why this cannot be spelled as three {@link #angle} calls.
         */
        public Part<S> bus(BusOf<S> bus) {
            return op(new Bussed<>(Objects.requireNonNull(bus, "bus")));
        }

        /** Plays one animation bus, with the render state driving the playhead in milliseconds. */
        public Part<S> keys(BusAnimationSequence sequence, FloatOf<S> playhead) {
            return op(new Keys<>(Objects.requireNonNull(sequence, "sequence"),
                    Objects.requireNonNull(playhead, "playhead")));
        }

        /**
         * Chooses this part's mesh from the render state: one slot in the model that holds whichever of
         * several shapes this block turns out to be — the twelve anvils, the satellite dishes, a boiler
         * before and after it bursts.
         *
         * <p>The chooser must always name a mesh. A part that sometimes draws nothing says so with
         * {@link #visibleIf}: whether a part occupies a slot at all is settled when the model is sealed,
         * and a part that came and went would shift every slot after it.
         */
        public Part<S> mesh(GeometryOf<S> geometry) {
            requireOpen();
            if (this.geometry == null) {
                throw new IllegalStateException("A joint draws nothing, so it has no mesh to choose");
            }
            this.mesh = Objects.requireNonNull(geometry, "geometry");
            return this;
        }

        /**
         * Chooses this part's surface from the render state: one mesh with several skins, which is how
         * this mod spells a machine tier, a dyed casing or an inserted battery.
         *
         * <p>Declaring the mesh once and letting the skin move is not the same as declaring it once per
         * skin. A part exists as one instance either way, and an instance that changes texture moves to
         * the bucket for that texture; a part per skin would allocate every bucket for every machine and
         * leave all but one of them hidden.
         */
        public Part<S> skin(SurfaceOf<S> surface) {
            requireOpen();
            if (geometry == null) {
                throw new IllegalStateException("A joint draws nothing, so it has no skin to choose");
            }
            this.skin = Objects.requireNonNull(surface, "surface");
            return this;
        }

        /** Draws this part, and everything under it, only when the render state says so. */
        public Part<S> visibleIf(Test<S> condition) {
            requireOpen();
            this.visible = Objects.requireNonNull(condition, "condition");
            return this;
        }

        /** Tints this part's vertices with an ARGB colour from the render state. */
        public Part<S> tint(IntOf<S> argb) {
            requireOpen();
            this.tint = Objects.requireNonNull(argb, "argb");
            return this;
        }

        /** Lights this part with something other than the light sampled at the block entity. */
        public Part<S> light(IntOf<S> packed) {
            requireOpen();
            this.light = Objects.requireNonNull(packed, "packed");
            return this;
        }

        /**
         * Draws this part after everything declared at a lower order, the way
         * {@link dev.golem.core.render.SubmitSink#order} does.
         *
         * <p>Declaration order alone settles nothing in the vanilla pass: geometry is batched by render
         * type, and two parts on different types come out in whatever order the batcher chose. A blended
         * shell over an opaque body needs the body drawn first to blend against, and this is how a
         * declaration says so.
         *
         * <p>The instanced backend has no equivalent lever and needs none — it draws by material, and a
         * blended surface already belongs to a later pass there than a cutout one. The order is
         * therefore the vanilla pass's tie-break, not a second scene graph, and a part that relies on it
         * for anything but blending against what is underneath is declaring the wrong thing.
         */
        public Part<S> order(int order) {
            requireOpen();
            this.order = order;
            return this;
        }

        private Part<S> op(Op<S> op) {
            requireOpen();
            ops.add(op);
            if (op.dynamic()) dynamic = true;
            return this;
        }

        public @Nullable Geometry geometry() {
            return geometry;
        }

        /** The mesh this part draws for this state, which is its own unless it chooses one. */
        public @Nullable Geometry geometryOf(S state) {
            return mesh == null ? geometry : mesh.of(state);
        }

        public @Nullable Surface surface() {
            return surface;
        }

        /** The surface this part draws with for this state, which is its own unless it names a skin. */
        public @Nullable Surface surfaceOf(S state) {
            return skin == null ? surface : skin.of(state);
        }

        public List<Part<S>> children() {
            return List.copyOf(children);
        }

        /** Whether this part's own transform reads the render state. */
        public boolean dynamic() {
            return dynamic;
        }

        /** Whether this part and everything under it is fixed. */
        public boolean rigidTree() {
            if (dynamic || visible != null || mesh != null || skin != null || tint != null || light != null) {
                return false;
            }
            for (Part<S> child : children) if (!child.rigidTree()) return false;
            return true;
        }

        /** Applies this part's own transform to {@code matrix}, which already holds its parents'. */
        public void applyTo(Matrix4f matrix, S state) {
            for (Op<S> op : ops) op.apply(matrix, state);
        }

        /** This part's local transform alone, for tests and for the instanced backend. */
        public Matrix4f local(S state) {
            Matrix4f matrix = new Matrix4f();
            applyTo(matrix, state);
            return matrix;
        }

        void applyTo(PoseStack stack, S state) {
            for (Op<S> op : ops) op.apply(stack, state);
        }

        /** Whether this part draws for this state. A part hidden here hides its children too. */
        public boolean visible(S state) {
            return visible == null || visible.of(state);
        }

        /** This part's ARGB vertex tint, or {@code -1} when it declared none. */
        public int tintOf(S state) {
            return tint == null ? -1 : tint.of(state);
        }

        /** This part's light, falling back to whatever the model or the engine supplied. */
        public int lightOf(S state, int fallback) {
            return light == null ? fallback : light.of(state);
        }

        /** The pass this part is drawn in; 0 unless it asked for a later one. */
        public int order() {
            return order;
        }

        private void seal() {
            sealed = true;
            for (Part<S> child : children) child.seal();
        }

        private void requireOpen() {
            if (sealed) throw new IllegalStateException("A gem part cannot be changed after the model is sealed");
        }
    }

    /**
     * Draws a declared model through the vanilla submit pass.
     *
     * <p>{@code defaultLight} is the light the engine sampled at the block entity or entity, used by
     * every part that does not name its own.
     */
    public static <S> void replay(Model<S> model, S state, PoseStack poseStack, SubmitSink sink,
                                  int defaultLight) {
        for (Part<S> root : model.roots) draw(root, state, poseStack, sink, model, defaultLight);
    }

    private static <S> void draw(Part<S> part, S state, PoseStack poseStack, SubmitSink sink,
                                 Model<S> model, int defaultLight) {
        if (!part.visible(state)) return;
        poseStack.pushPose();
        part.applyTo(poseStack, state);
        Geometry geometry = part.geometryOf(state);
        if (geometry != null) {
            int light = part.lightOf(state, model.lightOf(state, defaultLight));
            int overlay = model.overlayOf(state);
            int tint = part.tintOf(state);
            SubmitSink target = part.order() == 0 ? sink : sink.order(part.order());
            target.submitCustomGeometry(poseStack, part.surfaceOf(state).renderType(),
                    (pose, buffer) -> geometry.draw(pose, buffer, light, overlay, tint));
        }
        for (Part<S> child : part.children) draw(child, state, poseStack, sink, model, defaultLight);
        poseStack.popPose();
    }
}
