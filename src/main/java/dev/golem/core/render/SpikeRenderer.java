package dev.golem.core.render;

import dev.golem.core.Ids;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

//? if >=26.1 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Compile-only proof that a renderer written once against the Golem shape resolves on every supported
 * version. It mirrors the three things HBM's real renderers actually do — a stateful extract, an
 * ordered submit, and a camera-billboarded submit — without depending on HBM.
 *
 * <p>This is not registered or drawn; it exists so a build failure here catches an abstraction leak
 * before the mass migration touches ~100 renderers. Delete it once real renderers cover the surface.
 */
public final class SpikeRenderer implements CompatBER<BlockEntity, SpikeRenderer.State> {

    /** Mirrors HBM's pattern of a per-renderer state carrying extracted animation values. */
    public static final class State extends CompatRenderState {
        public float yaw;
    }

    private final RenderType layer = entityCutout(id("golemcore", "textures/spike.png"));

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extract(BlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition) {
        state.yaw = (blockEntity.getBlockPos().getY() + partialTicks) % 360F;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitSink sink, CameraInfo camera) {
        // Base geometry at the default order.
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        sink.submitCustomGeometry(poseStack, layer, (pose, buffer) ->
                //? if >=1.21 {
                buffer.addVertex(pose, 0F, 0F, 0F).setColor(-1).setUv(0F, 0F).setLight(state.lightCoords).setNormal(pose, 0F, 1F, 0F));
                //?} else {
                /*buffer.vertex(pose.pose(), 0F, 0F, 0F).color(-1).uv(0F, 0F).uv2(state.lightCoords).normal(pose.normal(), 0F, 1F, 0F).endVertex());
                *///?}
        poseStack.popPose();

        // A later-ordered, camera-facing submission — the pattern HBM uses for translucent effects
        // layered over the base model.
        sink.order(1).submitCustomGeometry(poseStack, layer, (pose, buffer) -> {
            Poses.rotate(pose, Axis.YP.rotationDegrees(-camera.yRot));
            //? if >=1.21 {
            buffer.addVertex(pose, 0F, 1F, 0F).setColor(-1).setUv(1F, 1F).setLight(state.lightCoords).setNormal(pose, 0F, 1F, 0F);
            //?} else {
            /*buffer.vertex(pose.pose(), 0F, 1F, 0F).color(-1).uv(1F, 1F).uv2(state.lightCoords).normal(pose.normal(), 0F, 1F, 0F).endVertex();
            *///?}
        });
    }

    // The two spellings each era uses, kept behind helpers so the body above stays version-agnostic.
    // The real module will expose these as CompatRenderTypes / Ids.
    private static RenderType entityCutout(
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture) {
        //? if >=26.1 {
        return RenderTypes.entityCutout(texture);
        //?} else {
        /*return RenderType.entityCutout(texture);
        *///?}
    }

    private static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            id(String namespace, String path) {
        //? if >=26.1 {
        return Ids.of(namespace, path);
        //?} elif >=1.21 {
        /*return Ids.of(namespace, path);
        *///?} else {
        /*return Ids.of(namespace, path);
        *///?}
    }
}
