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
