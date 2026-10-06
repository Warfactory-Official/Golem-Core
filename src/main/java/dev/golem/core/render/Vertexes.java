package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/** Stable vertex emission for the render API changes between supported Minecraft versions. */
public final class Vertexes {

    private Vertexes() {}

    /** Emits a fully attributed entity-format vertex. */
    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                              float x, float y, float z, int color, float u, float v,
                              int overlay, int light, float normalX, float normalY, float normalZ) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z)
                .color(color)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        *///?}
    }

    /**
     * {@code buffer} with every texture coordinate written through it mapped by {@code texture} first, as a 1.7
     * renderer's {@code GL_TEXTURE} matrix mapped it: {@code (u, v, 1) -> texture * (u, v, 1)}. Positions and every
     * other attribute pass through call for call (the posed {@code addVertex} forms to the buffer's own), so geometry
     * drawn through it lands on exactly the depth of the same geometry drawn directly - an {@code EQUAL}-depth glint
     * over its surface.
     */
    public static VertexConsumer uvTransformed(VertexConsumer buffer, org.joml.Matrix3fc texture) {
        return new UvTransformed(buffer, new org.joml.Matrix3f(texture));
    }

    private record UvTransformed(VertexConsumer delegate, org.joml.Matrix3f texture) implements VertexConsumer {
        private float mapU(float u, float v) { return texture.m00() * u + texture.m10() * v + texture.m20(); }

        private float mapV(float u, float v) { return texture.m01() * u + texture.m11() * v + texture.m21(); }

        //? if >=1.21 {
        @Override public VertexConsumer addVertex(float x, float y, float z) { delegate.addVertex(x, y, z); return this; }
        @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) { delegate.addVertex(pose, x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setColor(int argb) { delegate.setColor(argb); return this; }
        @Override public VertexConsumer setUv(float u, float v) { delegate.setUv(mapU(u, v), mapV(u, v)); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x, y, z); return this; }
        //?}
        //? if >=26.1 {
        @Override public VertexConsumer setLineWidth(float width) { delegate.setLineWidth(width); return this; }
        //?}
        //? if <1.21 {
        /*@Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer vertex(org.joml.Matrix4f matrix, float x, float y, float z) { delegate.vertex(matrix, x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r, g, b, a); return this; }
        @Override public VertexConsumer uv(float u, float v) { delegate.uv(mapU(u, v), mapV(u, v)); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
        @Override public void endVertex() { delegate.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r, g, b, a); }
        @Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
        *///?}
    }

    /** Emits a {@code POSITION_TEX} vertex, the glint pipeline's format. */
    public static void positionTex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z,
                                   float u, float v) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z).setUv(u, v);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z).uv(u, v).endVertex();
        *///?}
    }

    /** Float-channel counterpart of {@link #vertex(VertexConsumer, PoseStack.Pose, float, float, float, int, float, float, int, int, float, float, float)}. */
    public static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
                              float x, float y, float z, float red, float green, float blue, float alpha,
                              float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z)
                .color(red, green, blue, alpha)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        *///?}
    }

    /**
     * Emits a vertex of the line pipeline, which wants the segment's direction as its normal and, from
     * 26.1, a per-vertex width where earlier versions carried one width on the render type itself.
     */
    public static void line(VertexConsumer buffer, PoseStack.Pose pose,
                            float x, float y, float z, int color,
                            float normalX, float normalY, float normalZ) {
        //? if >=26.1 {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(lineWidth());
        //?} elif >=1.21 {
        /*buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setNormal(pose, normalX, normalY, normalZ);
        *///?} else {
        /*buffer.vertex(pose.pose(), x, y, z)
                .color(color)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        *///?}
    }

    /**
     * A line vertex at an explicit pixel width, for {@link LineRenderTypes#lines(float)} of the same width: 26.1
     * writes it into the vertex, older versions carry it in the render type and ignore it here.
     */
    public static void line(VertexConsumer buffer, PoseStack.Pose pose,
                            float x, float y, float z, int color,
                            float normalX, float normalY, float normalZ, float width) {
        //? if >=26.1 {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setNormal(pose, normalX, normalY, normalZ)
                .setLineWidth(width);
        //?} else {
        /*line(buffer, pose, x, y, z, color, normalX, normalY, normalZ);
        *///?}
    }

    //? if >=26.1 {
    /** The width vanilla gives its own block outline, scaled for the window. */
    private static float lineWidth() {
        return net.minecraft.client.Minecraft.getInstance().gameRenderer
                .getGameRenderState().windowRenderState.appropriateLineWidth;
    }
    //?}

    /** Emits a position/color vertex for render types that do not consume texture or light attributes. */
    public static void color(VertexConsumer buffer, PoseStack.Pose pose,
                             float x, float y, float z, int color) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z).setColor(color);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z).color(color).endVertex();
        *///?}
    }

    /** Float-channel counterpart of {@link #color(VertexConsumer, PoseStack.Pose, float, float, float, int)}. */
    public static void color(VertexConsumer buffer, PoseStack.Pose pose,
                             float x, float y, float z, float red, float green, float blue, float alpha) {
        //? if >=1.21 {
        buffer.addVertex(pose, x, y, z).setColor(red, green, blue, alpha);
        //?} else {
        /*buffer.vertex(pose.pose(), x, y, z).color(red, green, blue, alpha).endVertex();
        *///?}
    }
}
