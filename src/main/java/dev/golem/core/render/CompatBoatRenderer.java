package dev.golem.core.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.boat.Boat;
import org.joml.Quaternionf;
*///?}

/**
 * A boat renderer for a custom hull: one baked cube model and one skin, drawn with vanilla's boat
 * frame — the 0.375 lift, the hurt wobble, the bubble-column tilt, and the water patch that keeps the
 * hull's interior dry.
 *
 * <p>26.1's {@code AbstractBoatRenderer} owns that frame and asks only for the model; older versions
 * keep it private inside {@code BoatRenderer}, which is keyed by vanilla's boat variants and cannot
 * host a foreign hull, so the frame is restated there from vanilla's own sequence.
 */
public class CompatBoatRenderer
        //? if >=26.1 {
        extends AbstractBoatRenderer
        //?} else {
        /*extends EntityRenderer<Boat>
        *///?}
{

    //? if >=26.1 {
    private final EntityModel<BoatRenderState> hull;
    private final Model.Simple waterPatch;
    //?} else {
    /*private final ResourceLocation texture;
    private final ModelPart hull;
    private final ModelPart waterPatch;
    *///?}

    public CompatBoatRenderer(EntityRendererProvider.Context context,
                              //? if >=26.1 {
                              Identifier
                              //?} else {
                              /*ResourceLocation
                              *///?}
                              texture, LayerDefinition hull) {
        //? if >=26.1 {
        super(context, texture);
        this.hull = new EntityModel<>(hull.bakeRoot()) {};
        this.waterPatch = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH), ignored -> RenderTypes.waterMask());
        //?} else {
        /*super(context);
        this.texture = texture;
        this.hull = hull.bakeRoot();
        // This era bakes the patch into each vanilla boat's own layer; the oak one is any of them.
        this.waterPatch = context.bakeLayer(ModelLayers.createBoatModelName(Boat.Type.OAK)).getChild("water_patch");
        this.shadowRadius = 0.8F;
        *///?}
    }

    /** The baked hull, for a subclass that animates a part of it. */
    public ModelPart hull() {
        //? if >=26.1 {
        return hull.root();
        //?} else {
        /*return hull;
        *///?}
    }

    //? if >=26.1 {
    @Override
    protected EntityModel<BoatRenderState> model() {
        return hull;
    }

    @Override
    protected void submitTypeAdditions(BoatRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                       int lightCoords) {
        if (!state.isUnderWater) {
            collector.submitModel(waterPatch, Unit.INSTANCE, poseStack, texture, lightCoords,
                    OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        }
    }
    //?} else {
    /*@Override
    public void render(Boat boat, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.375F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        float hurt = boat.getHurtTime() - partialTicks;
        float damage = Math.max(boat.getDamage() - partialTicks, 0.0F);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * boat.getHurtDir()));
        }
        float bubble = boat.getBubbleAngle(partialTicks);
        if (!Mth.equal(bubble, 0.0F)) {
            poseStack.mulPose(new Quaternionf().setAngleAxis(bubble * Mth.DEG_TO_RAD, 1.0F, 0.0F, 1.0F));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
        hull.render(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
        if (!boat.isUnderWater()) {
            waterPatch.render(poseStack, bufferSource.getBuffer(RenderType.waterMask()), packedLight,
                    OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
        super.render(boat, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Boat boat) {
        return texture;
    }
    *///?}
}
