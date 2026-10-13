package dev.golem.core.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Draws baked {@link ModelPart} trees through a {@link SubmitSink}.
 *
 * <p>A part renders against a {@code PoseStack}, but a sink's geometry callback hands back only the
 * one {@code Pose} it was submitted under. Rebuilding a single-entry stack from that pose is the
 * bridge, and it is the same on every node. The part API itself has not moved since 1.17.
 */
public final class ModelParts {

    private ModelParts() {}

    /** Bakes a layer definition straight into its root part, for models no layer registry needs to know. */
    public static ModelPart bake(LayerDefinition definition) {
        return definition.bakeRoot();
    }

    /** Renders {@code part} at {@code pose} into an already-acquired buffer. */
    public static void render(PoseStack.Pose pose, ModelPart part, VertexConsumer buffer, int light, int overlay) {
        PoseStack local = new PoseStack();
        local.last().pose().set(pose.pose());
        local.last().normal().set(pose.normal());
        part.render(local, buffer, light, overlay);
    }

    /** Submits {@code part} as custom geometry drawn with {@code type}. */
    public static void submit(SubmitSink sink, PoseStack poseStack, RenderType type, ModelPart part, int light,
                              int overlay) {
        sink.submitCustomGeometry(poseStack, type, (pose, buffer) -> render(pose, part, buffer, light, overlay));
    }
}
