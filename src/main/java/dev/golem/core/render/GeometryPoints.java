package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.function.Consumer;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/** A sink that draws nothing and keeps every custom-geometry vertex position, in the sink's own space. */
public final class GeometryPoints implements SubmitSink {

    private float[] points = new float[3 * 256];
    private int size;

    private GeometryPoints() {}

    /** The positions of what {@code draw} submits as custom geometry, {@code x, y, z} after each other. */
    public static float[] of(Consumer<SubmitSink> draw) {
        GeometryPoints sink = new GeometryPoints();
        draw.accept(sink);
        return Arrays.copyOf(sink.points, sink.size);
    }

    private void add(float x, float y, float z) {
        if (size + 3 > points.length) points = Arrays.copyOf(points, points.length * 2);
        points[size++] = x;
        points[size++] = y;
        points[size++] = z;
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometry geometry) {
        geometry.render(poseStack.last(), new Positions());
    }

    @Override
    public SubmitSink order(int order) {
        return this;
    }

    @Override
    public void submitItem(PoseStack poseStack, CompatItemRenderState item, int light, int overlay, int seed) {}

    @Override
    public void submitBlock(PoseStack poseStack, CompatBlockRenderState block, int light, int overlay) {}

    @Override
    public void submitMovingBlock(PoseStack poseStack, CompatMovingBlock block) {}

    @Override
    public void submitBlockEntity(PoseStack poseStack, CompatBlockEntityRenderState blockEntity) {}

    @Override
    public void submitText(PoseStack poseStack, Font font, float x, float y, FormattedCharSequence text,
                           boolean shadow, Font.DisplayMode mode, int light, int color, int backgroundColor) {}

    /** Takes positions only; the posed forms land here after the default transform. */
    private final class Positions implements VertexConsumer {
        //? if >=1.21 {
        @Override public VertexConsumer addVertex(float x, float y, float z) { add(x, y, z); return this; }
        @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) {
            Vector3f at = pose.pose().transformPosition(x, y, z, new Vector3f());
            add(at.x, at.y, at.z);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int argb) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        //?}
        //? if >=26.1 {
        @Override public VertexConsumer setLineWidth(float width) { return this; }
        //?}
        //? if <1.21 {
        /*@Override public VertexConsumer vertex(double x, double y, double z) { add((float) x, (float) y, (float) z); return this; }
        @Override public VertexConsumer vertex(org.joml.Matrix4f matrix, float x, float y, float z) {
            Vector3f at = matrix.transformPosition(x, y, z, new Vector3f());
            add(at.x, at.y, at.z);
            return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
        *///?}
    }
}
