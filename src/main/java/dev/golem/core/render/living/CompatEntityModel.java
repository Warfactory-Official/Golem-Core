package dev.golem.core.render.living;

import java.util.function.Function;
import net.minecraft.client.model.geom.ModelPart;

//? if >=26.1 {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
*///?}

/**
 * A cube-model ({@code ModelPart} tree) for a living entity, written once against 26.1's shape: built from a
 * baked root and posed from a render state.
 *
 * <p>Older versions pose from the entity plus five loose floats and draw through an abstract
 * {@code renderToBuffer}; there this class owns the root, copies vanilla's five floats into the bound state and
 * forwards to {@link #pose}. The bound state is the one the {@link CompatMobRenderer} currently drawing the model
 * set before vanilla's pass.
 *
 * <p>Every part's pose is reset before {@link #pose} runs on both eras, so a model writes only the rotations it
 * animates.
 *
 * @param <S> the render state the model reads
 */
public abstract class CompatEntityModel<S extends CompatLivingRenderState>
        //? if >=26.1 {
        extends EntityModel<S>
        //?} else {
        /*extends EntityModel<LivingEntity>
        *///?}
{

    //? if <26.1 {
    /*private final ModelPart root;
    private final List<ModelPart> parts;
    @Nullable S bound;
    *///?}

    /** A model drawn with the cutout, unculled entity type (vanilla's default for cube models). */
    protected CompatEntityModel(ModelPart root) {
        //? if >=26.1 {
        super(root);
        //?} else {
        /*this(root, RenderType::entityCutoutNoCull);
        *///?}
    }

    /** A model drawn with a specific render type per texture, e.g. translucent or culled. */
    protected CompatEntityModel(ModelPart root, Function<
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            , RenderType> renderType) {
        //? if >=26.1 {
        super(root, renderType);
        //?} else {
        /*super(renderType);
        this.root = root;
        this.parts = root.getAllParts().toList();
        *///?}
    }

    /** Writes this frame's rotations into the parts. Every part starts at its baked pose. */
    protected abstract void pose(S state);

    //? if >=26.1 {
    @Override
    public final void setupAnim(S state) {
        super.setupAnim(state);
        pose(state);
    }
    //?} else {
    /*public final ModelPart root() {
        return root;
    }

    @Override
    public final void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                float netHeadYaw, float headPitch) {
        for (ModelPart part : parts) part.resetPose();
        S state = bound;
        if (state == null) return;
        state.walkAnimationPos = limbSwing;
        state.walkAnimationSpeed = limbSwingAmount;
        state.ageInTicks = ageInTicks;
        state.yRot = netHeadYaw;
        state.xRot = headPitch;
        state.attackTime = attackTime;
        state.isBaby = young;
        pose(state);
    }

    @Override
    //? if >=1.21 {
    public final void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay, int color) {
        root.render(poseStack, buffer, light, overlay, color);
    }
    //?} else {
    /^public final void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                                     float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, light, overlay, red, green, blue, alpha);
    }^/
    //?}
    *///?}
}
