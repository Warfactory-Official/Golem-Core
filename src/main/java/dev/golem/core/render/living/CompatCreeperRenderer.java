package dev.golem.core.render.living;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CreeperPowerLayer;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.model.monster.creeper.CreeperModel;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.client.model.CreeperModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;
*///?}

/**
 * Vanilla's creeper renderer with a different skin and an exaggerated swell, for creeper variants.
 *
 * <p>Vanilla's swell is {@code s^4}; the swell multiplier scales that before it is applied, so a value
 * of 5 makes a nuclear creeper balloon well past the vanilla one while keeping vanilla's wobble.
 * The charged-creeper overlay is vanilla's own unless an overlay texture is given, in which case vanilla's
 * power layer is swapped for one bound to that sheet (the 1.7 {@code RenderCreeperUniversal} armor texture).
 */
public class CompatCreeperRenderer extends CreeperRenderer {

    private final
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            texture;
    private final float swellMultiplier;

    public CompatCreeperRenderer(EntityRendererProvider.Context context,
                                 //? if >=26.1 {
                                 Identifier
                                 //?} else {
                                 /*ResourceLocation
                                 *///?}
                                 texture, float swellMultiplier) {
        this(context, texture, swellMultiplier, null);
    }

    public CompatCreeperRenderer(EntityRendererProvider.Context context,
                                 //? if >=26.1 {
                                 Identifier
                                 //?} else {
                                 /*ResourceLocation
                                 *///?}
                                 texture, float swellMultiplier,
                                 //? if >=26.1 {
                                 @Nullable Identifier
                                 //?} else {
                                 /*@Nullable ResourceLocation
                                 *///?}
                                 chargedOverlay) {
        super(context);
        this.texture = texture;
        this.swellMultiplier = swellMultiplier;
        if (chargedOverlay != null) {
            this.layers.removeIf(layer -> layer instanceof CreeperPowerLayer);
            addLayer(new Overlay(this, context.getModelSet(), chargedOverlay));
        }
    }

    /** Vanilla's power swirl on a different sheet. */
    //? if >=26.1 {
    private static final class Overlay extends CreeperPowerLayer {
        private final Identifier texture;

        Overlay(RenderLayerParent<CreeperRenderState, CreeperModel> parent, EntityModelSet models, Identifier texture) {
            super(parent, models);
            this.texture = texture;
        }

        @Override
        protected Identifier getTextureLocation() {
            return texture;
        }
    }
    //?} else {
    /*private static final class Overlay extends CreeperPowerLayer {
        private final ResourceLocation texture;

        Overlay(RenderLayerParent<Creeper, CreeperModel<Creeper>> parent, EntityModelSet models, ResourceLocation texture) {
            super(parent, models);
            this.texture = texture;
        }

        @Override
        protected ResourceLocation getTextureLocation() {
            return texture;
        }
    }
    *///?}

    /** Vanilla's swell scale with the multiplier folded in after the {@code s^4} curve. */
    private void swell(float swelling, PoseStack poseStack) {
        float wobble = 1.0F + Mth.sin(swelling * 100.0F) * swelling * 0.01F;
        float swell = Mth.clamp(swelling, 0.0F, 1.0F);
        swell *= swell;
        swell *= swell;
        swell *= swellMultiplier;
        poseStack.scale((1.0F + swell * 0.4F) * wobble, (1.0F + swell * 0.1F) / wobble,
                (1.0F + swell * 0.4F) * wobble);
    }

    //? if >=26.1 {
    @Override
    protected void scale(CreeperRenderState state, PoseStack poseStack) {
        swell(state.swelling, poseStack);
    }

    @Override
    public Identifier getTextureLocation(CreeperRenderState state) {
        return texture;
    }
    //?} else {
    /*@Override
    protected void scale(Creeper entity, PoseStack poseStack, float partialTicks) {
        swell(entity.getSwelling(partialTicks), poseStack);
    }

    @Override
    public ResourceLocation getTextureLocation(Creeper entity) {
        return texture;
    }
    *///?}
}
