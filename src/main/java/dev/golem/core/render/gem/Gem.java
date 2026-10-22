package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.golem.core.render.Poses;
import dev.golem.core.render.StaticMeshes;
import dev.golem.core.render.SubmitSink;
import dev.golem.core.render.anim.BusAnimationSequence;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A model a renderer declares instead of drawing.
 *
 * <p>A renderer that writes its own {@code submit} can only feed the vanilla pass: the geometry exists for one
 * call and the transforms live in a {@code PoseStack} that is gone by the next frame. The instanced backend
 * needs every bucket of (mesh, surface) known before the first frame, rigid transforms written once, and only
 * the moving ones touched again. So the renderer states its model once and each backend reads that statement.
 *
 * <p>A model is a tree of {@linkplain Part parts}. Each part carries an ordered list of transform operations
 * and, usually, one lump of {@link Geometry} drawn with one {@link Surface}; a part with no geometry is a joint
 * that only places its children. <b>Operations apply in declaration order, which is a {@code PoseStack}'s call
 * order</b>: {@code translate} then {@code rotate} does what {@code poseStack.translate} then
 * {@code poseStack.mulPose} did, so an existing renderer transcribes line by line.
 *
 * <p>An operation is either fixed or read from the render state. A part with no state-driven operation is
 * <em>rigid</em>, and a rigid part under rigid parents never has its transform recomputed, which is why a
 * static machine is cheap to instance. Animation is declared three ways:
 *
 * <ul>
 *   <li>{@link Part#angle} / {@link Part#slide} / {@link Part#spin} bind one float from the render
 *       state straight to one axis. Most machines animate on exactly one float.</li>
 *   <li>{@link Part#keys} plays a {@link BusAnimationSequence} whose playhead is driven by the render
 *       state rather than by the clock, for a machine whose progress is a number, not an elapsed time.</li>
 *   <li>Procedural motion (an arm that picks random targets and speed-limits toward them) binds its joint
 *       angles through {@link Part#angle} and stays procedural.</li>
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
     * <p>The matrix form is what the instanced backend and the tests evaluate; the pose-stack form is what
     * the vanilla pass replays. They are a pair rather than derived from each other because
     * {@code PoseStack} also maintains a normal matrix, and the eras differ on how a raw matrix multiplied
     * into it updates that. Real {@code PoseStack} calls keep the vanilla output equal to the hand-written
     * renderer; {@code GemOpAgreementTest} checks the pair agree.
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
     * <p>The order is the weapon renderers': translate, the three rotations in the bus's own axis order, the
     * pivot subtracted, then scale, so a machine and a gun read the same keyframe table identically.
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
        private boolean resident;
        private @Nullable IntOf<S> light;
        private @Nullable IntOf<S> overlay;
        private boolean sealed;
        private @Nullable List<Part<S>> frozenRoots;

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

        /**
         * Keeps this model's meshes on the GPU for {@link Gem#replay}: each part is uploaded once and drawn with
         * the frame's pose instead of being written vertex by vertex every frame. The picture is the immediate
         * draw's; see {@link dev.golem.core.render.StaticMeshes} for what that means and when it falls back.
         */
        public Model<S> resident() {
            requireOpen();
            this.resident = true;
            return this;
        }

        public boolean isResident() {
            return resident;
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
         * <p>Block entities and free entities are drawn without one. An item model is handed a real overlay
         * (the hurt flash of whoever holds it) and must pass it on.
         */
        public Model<S> overlay(IntOf<S> overlay) {
            requireOpen();
            this.overlay = Objects.requireNonNull(overlay, "overlay");
            return this;
        }

        /**
         * This model's top-level parts.
         *
         * <p>Frozen at {@link #seal}, like {@link Part#children}: the instanced walk starts here every frame,
         * and a fresh copy each time would be a per-frame allocation.
         */
        public List<Part<S>> roots() {
            return frozenRoots == null ? List.copyOf(roots) : frozenRoots;
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
         * <p>The model's own channels count. Fixed parts under a model that chooses its overlay from the
         * state are not rigid: the instanced backend writes a rigid model once, so the chooser would be
         * sampled on the first frame only. Light is the exception: a light change re-writes the visual
         * through {@code updateLight}, not through the rigidity check.
         */
        public boolean rigid() {
            if (overlay != null) return false;
            for (Part<S> root : roots) if (!root.rigidTree()) return false;
            return true;
        }

        /**
         * Whether the model-wide light and overlay channels are both fixed.
         *
         * <p>Every part falls back to these, so a chooser here is render state a part alone does not show.
         * No part is fixed under one, so the instanced walk asks this once before trusting any part.
         */
        public boolean channelsFixed() {
            return light == null && overlay == null;
        }

        /**
         * Closes the model to further declaration. The instanced backend allocates against the sealed tree,
         * so a part added later would exist on the vanilla path and be invisible on the instanced one.
         *
         * <p>A model with no parts is allowed: a machine whose appearance is built rather than placed (a
         * pool of melt, a beam) draws entirely through {@code submitUndeclared}.
         */
        public Model<S> seal() {
            sealed = true;
            frozenRoots = List.copyOf(roots);
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
        private int subtreeNodes;
        private int subtreeSlots;
        private @Nullable List<Part<S>> frozen;

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
         * Scales along one axis by a factor the render state supplies: a tank's contents standing as
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
         * Rotates about one axis through a pivot other than this part's origin, the shape a spinning
         * fan or rotor mounted away from the model centre needs.
         */
        public Part<S> spin(Axes axis, float pivotX, float pivotY, float pivotZ, FloatOf<S> degrees) {
            return translate(pivotX, pivotY, pivotZ)
                    .angle(axis, degrees)
                    .translate(-pivotX, -pivotY, -pivotZ);
        }

        /**
         * Applies a transform the render state has already composed, for a renderer that builds its
         * bones as matrices rather than as a sequence of named steps.
         *
         * <p>This is the one op whose two forms are not the same call twice: the pose-stack side multiplies a
         * raw matrix in, which updates the stack's normal matrix by its own rule rather than the axis-by-axis
         * one of the other ops. A renderer that hands out matrices drew this way, and matching it keeps the
         * vanilla output equal.
         */
        public Part<S> pose(PoseOf<S> pose) {
            return op(new Posed<>(Objects.requireNonNull(pose, "pose")));
        }

        /**
         * Applies one animation bus the render state hands over whole.
         *
         * <p>{@link #keys} samples a bus out of a keyframe table; this takes one that is already live, as a
         * held weapon's joints do. The rotations apply in the order the bus names; the axis order is data,
         * not a fixed X-Y-Z, so this cannot be spelled as three {@link #angle} calls.
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
         * Chooses this part's mesh from the render state: one slot that holds whichever of several shapes
         * the block turns out to be (the twelve anvils, the satellite dishes, a boiler before and after it
         * bursts).
         *
         * <p>The chooser must always name a mesh. A part that sometimes draws nothing says so with
         * {@link #visibleIf}: slot occupancy is settled when the model is sealed, and a part that came and
         * went would shift every later slot.
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
         * Chooses this part's surface from the render state: one mesh with several skins (a machine tier, a
         * dyed casing, an inserted battery).
         *
         * <p>An instance that changes texture moves to the bucket for that texture; a part per skin would
         * allocate every bucket for every machine and hide all but one.
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
         * <p>Declaration order alone settles nothing in the vanilla pass: geometry is batched by render type,
         * and parts on different types come out in the batcher's order. A blended shell over an opaque body
         * needs the body drawn first, and this says so.
         *
         * <p>The instanced backend needs no such lever: it draws by material, and a blended surface already
         * belongs to a later pass than a cutout one. The order is the vanilla pass's tie-break, for blending
         * against what is underneath, not a second scene graph.
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

        /**
         * This part's children.
         *
         * <p>Frozen once at {@link Model#seal}, so the instanced walk, which asks every part every frame, gets
         * the same immutable list instead of a copy. Before sealing the list is still growing.
         */
        public List<Part<S>> children() {
            return frozen == null ? List.copyOf(children) : frozen;
        }

        /** Whether this part's own transform reads the render state. */
        public boolean dynamic() {
            return dynamic;
        }

        /**
         * Whether this part's own contribution is fixed, whatever its children do.
         *
         * <p>{@link #rigidTree} asks the same question downwards. The instanced walk needs this upward one: a
         * part is only safe to leave alone when every ancestor is fixed too, since a fixed part under a
         * spinning one moves.
         */
        public boolean fixed() {
            return !dynamic && visible == null && mesh == null && skin == null && tint == null
                    && light == null;
        }

        /** Whether this part and everything under it is fixed. */
        public boolean rigidTree() {
            if (!fixed()) return false;
            for (Part<S> child : children) if (!child.rigidTree()) return false;
            return true;
        }

        /**
         * How many parts this subtree walks, this one included.
         *
         * <p>Sealed models only. The instanced walk numbers its cached poses by visit order, so skipping an
         * unchanged subtree advances that counter by exactly this many.
         */
        public int subtreeNodes() {
            return subtreeNodes;
        }

        /**
         * How many instance slots this subtree occupies.
         *
         * <p>Sealed models only, and only meaningful where {@link #rigidTree} holds: a part that chooses its
         * mesh from the render state can draw or not draw from frame to frame, so its subtree has no settled
         * slot count and is never rigid.
         */
        public int subtreeSlots() {
            return subtreeSlots;
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

        /**
         * Whether the instanced backend can draw this part. A blended surface needs the submit pass's sort and an
         * ordered part its bucket. Since the submit pass draws a machine's parts in walk order, the first such part
         * that draws and every part drawn after it stay with the vanilla renderer ({@link #replayUninstanced}).
         */
        public boolean instanced(S state) {
            Surface drawn = surfaceOf(state);
            return order == 0 && drawn != null && !drawn.blended();
        }

        private void seal() {
            sealed = true;
            frozen = List.copyOf(children);
            subtreeNodes = 1;
            subtreeSlots = geometry == null ? 0 : 1;
            for (Part<S> child : children) {
                child.seal();
                subtreeNodes += child.subtreeNodes;
                subtreeSlots += child.subtreeSlots;
            }
        }

        private void requireOpen() {
            if (sealed) throw new IllegalStateException("A gem part cannot be changed after the model is sealed");
        }
    }

    /**
     * The corners of every part a model draws in one state, carried through the same walk the draw uses
     * and into {@code start}'s frame.
     *
     * <p>This is the box a model is framed on: the declaration's box, not the mesh's. A rest pose that turns
     * or offsets what it holds (most of them) displaces the drawing from the mesh's bounds by that
     * transform, so framing on the mesh would place the picture around geometry nobody draws there.
     *
     * <p>Nothing here reads a clock: the state is the caller's, so a moving model is framed by sampling
     * this over the phases it moves through.
     */
    public static <S> void extents(Model<S> model, S state, Matrix4fc start, Consumer<Vector3fc> output) {
        for (Part<S> root : model.roots) corners(root, state, new Matrix4f(start), output);
    }

    private static <S> void corners(Part<S> part, S state, Matrix4f parent, Consumer<Vector3fc> output) {
        if (!part.visible(state)) return;
        Matrix4f here = new Matrix4f(parent);
        part.applyTo(here, state);
        Geometry geometry = part.geometryOf(state);
        float[][] box = geometry == null ? null : geometry.bounds();
        if (box != null) {
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                output.accept(here.transformPosition(new Vector3f(box[x][0], box[y][1], box[z][2])));
            }
        }
        for (Part<S> child : part.children) corners(child, state, here, output);
    }

    /**
     * Draws a declared model through the vanilla submit pass.
     *
     * <p>{@code defaultLight} is the light the engine sampled at the block entity or entity, used by
     * every part that does not name its own.
     */
    public static <S> void replay(Model<S> model, S state, PoseStack poseStack, SubmitSink sink,
                                  int defaultLight) {
        for (Part<S> root : model.roots) draw(root, state, poseStack, sink, model, defaultLight, null);
    }

    /** {@link #replay} from the first part {@link Part#instanced} refuses onward: what an instanced visual leaves. */
    public static <S> void replayUninstanced(Model<S> model, S state, PoseStack poseStack, SubmitSink sink,
                                             int defaultLight) {
        boolean[] reached = {false};
        for (Part<S> root : model.roots) draw(root, state, poseStack, sink, model, defaultLight, reached);
    }

    /** What {@link #walk} hands over for each part that would draw. */
    @FunctionalInterface
    public interface Visitor<S> {
        void part(Part<S> part, Geometry geometry, PoseStack poseStack);
    }

    /**
     * Walks a declared model exactly as {@link #replay} draws it - the same visibility tests and the same pose-stack
     * calls - and hands every part that would draw its geometry, with the pose stack as the draw had it.
     *
     * <p>For a pass that has to land on the very depth of a declared part, such as a 1.7 {@code GL_EQUAL} glint over
     * it. A pose rebuilt through {@link Part#applyTo(Matrix4f, Object)} cannot promise that: the matrix form rotates by
     * angle, the stack by quaternion, and the last bits differ.
     */
    public static <S> void walk(Model<S> model, S state, PoseStack poseStack, Visitor<S> visitor) {
        for (Part<S> root : model.roots) visit(root, state, poseStack, visitor);
    }

    private static <S> void visit(Part<S> part, S state, PoseStack poseStack, Visitor<S> visitor) {
        if (!part.visible(state)) return;
        poseStack.pushPose();
        part.applyTo(poseStack, state);
        Geometry geometry = part.geometryOf(state);
        if (geometry != null) visitor.part(part, geometry, poseStack);
        for (Part<S> child : part.children) visit(child, state, poseStack, visitor);
        poseStack.popPose();
    }

    private static <S> void draw(Part<S> part, S state, PoseStack poseStack, SubmitSink sink,
                                 Model<S> model, int defaultLight, boolean @Nullable [] reached) {
        if (!part.visible(state)) return;
        poseStack.pushPose();
        part.applyTo(poseStack, state);
        Geometry geometry = part.geometryOf(state);
        if (geometry != null && reached != null && !part.instanced(state)) reached[0] = true;
        if (geometry != null && (reached == null || reached[0])) {
            int light = part.lightOf(state, model.lightOf(state, defaultLight));
            int overlay = model.overlayOf(state);
            int tint = part.tintOf(state);
            SubmitSink target = part.order() == 0 ? sink : sink.order(part.order());
            if (model.resident) {
                StaticMeshes.submit(target, poseStack, part.surfaceOf(state), geometry, light,
                        overlay, tint);
            } else {
                target.submitCustomGeometry(poseStack, part.surfaceOf(state).renderType(),
                        (pose, buffer) -> geometry.draw(pose, buffer, light, overlay, tint));
            }
        }
        for (Part<S> child : part.children) draw(child, state, poseStack, sink, model, defaultLight, reached);
        poseStack.popPose();
    }
}
