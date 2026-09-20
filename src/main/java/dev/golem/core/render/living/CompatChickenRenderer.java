package dev.golem.core.render.living;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

//? if >=26.1 {
import net.minecraft.client.renderer.entity.state.ChickenRenderState;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.chicken.Chicken;
*///?}

/**
 * Vanilla's chicken renderer with a different skin, shadow and size, for chicken-bodied mobs. The
 * flap animation is vanilla's, read from the chicken's own flap fields on every node.
 */
public class CompatChickenRenderer extends ChickenRenderer {

    private final
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture;
    private final float scale;

    public CompatChickenRenderer(EntityRendererProvider.Context context,
                                 //? if >=26.1 {
                                 Identifier
                                 //?} else {
                                 /*ResourceLocation
                                 *///?}
                                 texture, float shadow, float scale) {
        super(context);
        this.texture = texture;
        this.scale = scale;
        this.shadowRadius = shadow;
    }

    //? if >=26.1 {
    @Override
    protected void scale(ChickenRenderState state, PoseStack poseStack) {
        if (scale != 1F) poseStack.scale(scale, scale, scale);
    }

    @Override
    public Identifier getTextureLocation(ChickenRenderState state) {
        return texture;
    }
    //?} else {
    /*@Override
    protected void scale(Chicken entity, PoseStack poseStack, float partialTicks) {
        if (scale != 1F) poseStack.scale(scale, scale, scale);
    }

    @Override
    public ResourceLocation getTextureLocation(Chicken entity) {
        return texture;
    }
    *///?}
}
