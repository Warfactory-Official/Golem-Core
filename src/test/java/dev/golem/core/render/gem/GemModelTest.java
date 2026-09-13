package dev.golem.core.render.gem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.golem.core.render.anim.BusAnimationSequence;
import java.util.List;
import net.minecraft.util.Mth;
import java.util.function.Consumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * The declaration's transform maths, which is the half of gemrender that can be wrong without anyone
 * noticing until a machine is visibly in the wrong place.
 */
final class GemModelTest {

    private static final float EPSILON = 1.0E-5F;

    /** A stand-in render state: one angle and one switch is enough to exercise every binding. */
    private static final class Probe {
        float value;
        boolean shown = true;
        int variant;
        final Matrix4f handed = new Matrix4f();
    }

    /** A named lump of nothing: the choosers care which one they were handed, not what is in it. */
    private record Lump(String name) implements Geometry {
        @Override public void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {}
        @Override public float[] vertices() { return new float[0]; }
        @Override public int[] indices() { return new int[0]; }
        @Override public int vertexCount() { return 0; }
    }

    private static Gem.Part<Probe> part(Consumer<Gem.Part<Probe>> declaration) {
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> part = model.joint();
        declaration.accept(part);
        return part;
    }

    private static Matrix4f viaPoseStack(Gem.Part<Probe> part, Probe state) {
        PoseStack stack = new PoseStack();
        part.applyTo(stack, state);
        return new Matrix4f(stack.last().pose());
    }

    private static void assertSameInstance(Object expected, Object actual, String what) {
        assertTrue(expected == actual, what + " should be the same list once the model is sealed");
    }

    private static void assertSame(Matrix4f expected, Matrix4f actual, String what) {
        assertTrue(expected.equals(actual, EPSILON), what + "\nexpected " + expected + "\nactual   " + actual);
    }

    @Test
    void operationsApplyInDeclarationOrderJustAsAPoseStackWould() {
        Gem.Part<Probe> declared = part(p -> p
                .translate(0.5F, 0F, 0.5F)
                .rotate(Gem.Axes.Y, 90F)
                .scale(0.0625F));

        PoseStack expected = new PoseStack();
        expected.translate(0.5F, 0F, 0.5F);
        expected.mulPose(Axis.YP.rotationDegrees(90F));
        expected.scale(0.0625F, 0.0625F, 0.0625F);

        assertSame(new Matrix4f(expected.last().pose()), declared.local(new Probe()),
                "a declaration must compose exactly as the pose-stack calls it replaces");
    }

    /**
     * Every operation carries a matrix form and a pose-stack form, and the two backends each read only
     * one of them. A disagreement would put a machine in one place on five nodes and another on the
     * sixth, which is precisely the bug the single declaration exists to make impossible.
     */
    @Test
    void everyOperationsTwoFormsAgree() {
        BusAnimationSequence bus = new BusAnimationSequence()
                .addPos(1D, 2D, 3D, 0)
                .addRot(10D, 20D, 30D, 0);
        bus.offset = new double[] {0.25D, 0.5D, 0.75D};

        List<Gem.Part<Probe>> declarations = List.of(
                part(p -> p.translate(1F, -2F, 3F)),
                part(p -> p.rotate(Gem.Axes.X, 37F)),
                part(p -> p.rotate(Gem.Axes.Y, -114F)),
                part(p -> p.rotate(Gem.Axes.Z, 180F)),
                part(p -> p.scale(2F, 0.5F, 1.5F)),
                part(p -> p.slide(Gem.Axes.Y, s -> s.value)),
                part(p -> p.angle(Gem.Axes.Z, s -> s.value)),
                part(p -> p.spin(Gem.Axes.X, 0F, 1.5F, 0F, s -> s.value)),
                part(p -> p.stretch(Gem.Axes.Y, s -> 1F + s.value / 360F)),
                part(p -> p.stretch(s -> 1F + s.value / 360F)),
                part(p -> p.pose(s -> s.handed)),
                part(p -> p.keys(bus, s -> s.value)));

        Probe state = new Probe();
        for (float value : new float[] {0F, 1F, 45F, -90F, 360F}) {
            state.value = value;
            state.handed.translation(value, 1F, -value).rotateZ(value * Mth.DEG_TO_RAD);
            for (Gem.Part<Probe> declared : declarations) {
                assertSame(viaPoseStack(declared, state), declared.local(state),
                        "the two forms disagree at " + value);
            }
        }
    }

    @Test
    void spinTurnsAboutItsPivotAndLeavesThePivotWhereItIs() {
        Gem.Part<Probe> fan = part(p -> p.spin(Gem.Axes.X, 0F, 1.5F, 0F, s -> s.value));
        Probe state = new Probe();
        state.value = 90F;

        Vector3f pivot = fan.local(state).transformPosition(new Vector3f(0F, 1.5F, 0F));
        assertEquals(0F, pivot.x(), EPSILON);
        assertEquals(1.5F, pivot.y(), EPSILON);
        assertEquals(0F, pivot.z(), EPSILON);

        Vector3f above = fan.local(state).transformPosition(new Vector3f(0F, 2.5F, 0F));
        assertEquals(1.5F, above.y(), EPSILON, "a quarter turn about X carries +Y onto +Z");
        assertEquals(1F, above.z(), EPSILON);
    }

    @Test
    void aChildInheritsItsParentsTransform() {
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> ring = model.joint().translate(0F, 1F, 0F).rotate(Gem.Axes.Y, 90F);
        Gem.Part<Probe> arm = ring.joint().translate(1F, 0F, 0F);

        Probe state = new Probe();
        Matrix4f accumulated = ring.local(state);
        arm.applyTo(accumulated, state);

        Vector3f tip = accumulated.transformPosition(new Vector3f());
        assertEquals(0F, tip.x(), EPSILON);
        assertEquals(1F, tip.y(), EPSILON);
        assertEquals(-1F, tip.z(), EPSILON, "a 90 degree yaw carries the arm's +X onto -Z");
    }

    @Test
    void aPartIsRigidUntilSomethingAboutItReadsTheState() {
        assertTrue(part(p -> p.translate(0F, 1F, 0F).rotate(Gem.Axes.Y, 90F)).rigidTree());
        assertFalse(part(p -> p.angle(Gem.Axes.Y, s -> s.value)).rigidTree());
        assertFalse(part(p -> p.translate(0F, 1F, 0F).visibleIf(s -> s.shown)).rigidTree());
        assertFalse(part(p -> p.pose(s -> s.handed)).rigidTree());

        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> root = model.joint().translate(0F, 1F, 0F);
        assertTrue(model.rigid());
        root.joint().angle(Gem.Axes.X, s -> s.value);
        assertFalse(model.rigid(), "a moving child makes the whole tree dynamic");
    }

    /**
     * The bus order is the one the weapon renderers already apply — translate, the three rotations in
     * the bus's own axis order, the pivot subtracted, then scale — so a keyframe table written for a
     * gun reads identically on a machine.
     */
    @Test
    void aBusPlaysInTheOrderTheWeaponRenderersUse() {
        BusAnimationSequence bus = new BusAnimationSequence()
                .addPos(1D, 2D, 3D, 0)
                .addRot(15D, 25D, 35D, 0);
        bus.offset = new double[] {0.5D, 0D, -0.5D};

        PoseStack expected = new PoseStack();
        double[] t = bus.getTransformation(0);
        expected.translate(t[0], t[1], t[2]);
        for (int i = 0; i < 3; i++) {
            int axis = (int) t[12 + i];
            float degrees = (float) t[3 + axis];
            if (axis == 0) expected.mulPose(Axis.XP.rotationDegrees(degrees));
            else if (axis == 1) expected.mulPose(Axis.YP.rotationDegrees(degrees));
            else expected.mulPose(Axis.ZP.rotationDegrees(degrees));
        }
        expected.translate(-t[9], -t[10], -t[11]);
        expected.scale((float) t[6], (float) t[7], (float) t[8]);

        Gem.Part<Probe> declared = part(p -> p.keys(bus, s -> s.value));
        assertSame(new Matrix4f(expected.last().pose()), declared.local(new Probe()),
                "a bus must play exactly as applyBus plays it");
    }

    @Test
    void anUndeclaredBusRestsAtTheIdentity() {
        Gem.Part<Probe> declared = part(p -> p.keys(new BusAnimationSequence(), s -> s.value));
        assertSame(new Matrix4f(), declared.local(new Probe()),
                "an empty bus must leave scale at one and everything else at zero");
    }

    @Test
    void aSealedModelRefusesToChange() {
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> root = model.joint().translate(0F, 1F, 0F);
        model.seal();

        assertThrows(IllegalStateException.class, () -> root.translate(1F, 0F, 0F));
        assertThrows(IllegalStateException.class, model::joint);
        assertThrows(IllegalStateException.class, () -> root.joint());
    }

    /**
     * A mesh or a skin read from the state is what a machine with several shapes or several paint jobs
     * declares instead of one part per combination — and it is also what stops that part being rigid,
     * because the bucket it draws from can move.
     */
    @Test
    void aPartCanBeHandedItsMeshAndItsSkinByTheState() {
        Geometry first = new Lump("first");
        Geometry second = new Lump("second");
        Surface red = Surface.cutout("golem:textures/red.png");
        Surface blue = Surface.cutout("golem:textures/blue.png");

        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> part = model.part(first, red)
                .mesh(s -> s.variant == 0 ? first : second)
                .skin(s -> s.variant == 0 ? red : blue);

        Probe state = new Probe();
        assertEquals(first, part.geometryOf(state));
        assertEquals(red, part.surfaceOf(state));

        state.variant = 1;
        assertEquals(second, part.geometryOf(state));
        assertEquals(blue, part.surfaceOf(state));

        assertFalse(model.rigid(), "a part whose bucket moves is not written once");
        assertThrows(IllegalStateException.class, () -> model.joint().mesh(s -> first));
        assertThrows(IllegalStateException.class, () -> model.joint().skin(s -> red));
    }

    @Test
    void stretchScalesTheAxisItNamesAndLeavesTheOthers() {
        Gem.Part<Probe> tank = part(p -> p.stretch(Gem.Axes.Y, s -> s.value));
        Probe state = new Probe();
        state.value = 0.25F;

        Vector3f corner = tank.local(state).transformPosition(new Vector3f(1F, 1F, 1F));
        assertEquals(1F, corner.x(), EPSILON);
        assertEquals(0.25F, corner.y(), EPSILON);
        assertEquals(1F, corner.z(), EPSILON);
    }

    /**
     * {@code rigidTree} asks whether a subtree can be left alone; {@code fixed} asks whether one part
     * contributes anything new. The instanced walk needs both, because it descends: it proves a
     * parent still, and only then is a still child still in the world rather than merely still
     * relative to something that moved.
     */
    @Test
    void aFixedPartUnderAMovingOneIsNotItselfStill() {
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> arm = model.joint().angle(Gem.Axes.Y, s -> s.value);
        Gem.Part<Probe> bolt = arm.joint().translate(0F, 1F, 0F);
        model.seal();

        assertTrue(bolt.fixed(), "the bolt declares nothing that reads the state");
        assertTrue(bolt.rigidTree(), "and neither does anything under it");
        assertFalse(arm.fixed(), "but the arm it is bolted to turns");
        assertFalse(arm.rigidTree());
        assertFalse(model.rigid());
    }

    @Test
    void aSealedSubtreeCountsItsOwnPartsAndSlots() {
        Surface skin = Surface.cutout("golem:textures/skin.png");
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> housing = model.part(new Lump("housing"), skin);
        Gem.Part<Probe> pivot = housing.joint().translate(0F, 1F, 0F);
        pivot.child(new Lump("blade"), skin);
        pivot.child(new Lump("hub"), skin);
        model.seal();

        assertEquals(4, housing.subtreeNodes(), "housing, the joint, and two children");
        assertEquals(3, housing.subtreeSlots(), "the joint carries no geometry, so no instance");
        assertEquals(3, pivot.subtreeNodes());
        assertEquals(2, pivot.subtreeSlots());
    }

    /**
     * The walk asks every part for its children every frame, so a fresh copy each time is a per-frame
     * allocation that can only ever return the same thing.
     */
    @Test
    void aSealedTreeHandsBackTheSameListEveryTime() {
        Gem.Model<Probe> model = Gem.model();
        Gem.Part<Probe> root = model.joint();
        root.joint();
        model.seal();

        assertSameInstance(model.roots(), model.roots(), "roots");
        assertSameInstance(root.children(), root.children(), "children");
    }

    /**
     * A part's own answer is not the whole answer: it falls back to the model for light and overlay,
     * so a chooser up there is state that no part can see and nothing below it may be left alone.
     */
    @Test
    void aModelWideChooserIsStateNoPartCanSee() {
        Gem.Model<Probe> plain = Gem.model();
        plain.joint().translate(0F, 1F, 0F);
        assertTrue(plain.seal().channelsFixed());

        Gem.Model<Probe> lit = Gem.model();
        lit.light(s -> s.variant);
        lit.joint().translate(0F, 1F, 0F);
        assertFalse(lit.seal().channelsFixed());
        assertTrue(lit.rigid(), "a model-wide light does not by itself make the tree dynamic");

        Gem.Model<Probe> overlaid = Gem.model();
        overlaid.overlay(s -> s.variant);
        overlaid.joint().translate(0F, 1F, 0F);
        assertFalse(overlaid.seal().channelsFixed());
    }

    @Test
    void aModelThatDeclaresNothingIsStillAModel() {
        Gem.Model<Probe> model = Gem.<Probe>model().seal();

        assertTrue(model.roots().isEmpty());
        assertTrue(model.rigid());
        assertThrows(IllegalStateException.class, model::joint);
    }
}
