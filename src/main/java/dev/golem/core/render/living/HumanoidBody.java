package dev.golem.core.render.living;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

//? if >=26.1 {
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * A humanoid body for a {@link CompatHumanoidMobRenderer}, as an opaque handle: vanilla's
 * {@code HumanoidModel} is generic over the entity below 26.1 and over the render state from 26.1 on,
 * so a mod names the body and never spells its type argument.
 *
 * <p>The zombie and skeleton bodies are 26.1's {@code ZombieModel} over vanilla's zombie and skeleton
 * layers, both arms held out when aggressive. Below 26.1 {@code ZombieModel} demands a {@code Zombie}
 * entity, so the same pose is applied through {@code AnimationUtils#animateZombieArms}, which is the
 * routine {@code ZombieModel} itself calls there.
 *
 * @param <T> the mob the body is drawn on
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class HumanoidBody<T extends net.minecraft.world.entity.Mob> {

    //? if >=26.1 {
    final HumanoidModel<CompatHumanoidRenderState> model;

    private HumanoidBody(HumanoidModel<CompatHumanoidRenderState> model) {
        this.model = model;
    }
    //?} else {
    /*final HumanoidModel<T> model;

    private HumanoidBody(HumanoidModel<T> model) {
        this.model = model;
    }
    *///?}

    /**
     * A bare biped baked from {@code layer}: {@code HumanoidModel.createMesh} at the sheet size the
     * skin was drawn for.
     *
     * @param translucent whether the skin blends rather than alpha-tests, for a ghost
     */
    public static <T extends net.minecraft.world.entity.Mob> HumanoidBody<T> plain(LayerDefinition layer,
                                                                                   boolean translucent) {
        //? if >=26.1 {
        java.util.function.Function<net.minecraft.resources.Identifier, net.minecraft.client.renderer.rendertype.RenderType> type =
                translucent ? RenderTypes::entityTranslucent : RenderTypes::entityCutout;
        return new HumanoidBody<>(new HumanoidModel<>(layer.bakeRoot(), type));
        //?} else {
        /*java.util.function.Function<net.minecraft.resources.ResourceLocation, RenderType> type =
                translucent ? RenderType::entityTranslucent : RenderType::entityCutoutNoCull;
        return new HumanoidBody<>(new HumanoidModel<>(layer.bakeRoot(), type));
        *///?}
    }

    /** Vanilla's zombie body: arms out while aggressive. */
    public static <T extends net.minecraft.world.entity.Mob> HumanoidBody<T> zombie(EntityRendererProvider.Context context) {
        return undead(context.bakeLayer(ModelLayers.ZOMBIE));
    }

    /** Vanilla's skeleton body (the thin-armed sheet) posed like a zombie's, which is 1.7's ModelSkeletonNT. */
    public static <T extends net.minecraft.world.entity.Mob> HumanoidBody<T> skeleton(EntityRendererProvider.Context context) {
        return undead(context.bakeLayer(ModelLayers.SKELETON));
    }

    private static <T extends net.minecraft.world.entity.Mob> HumanoidBody<T> undead(
            net.minecraft.client.model.geom.ModelPart root) {
        //? if >=26.1 {
        return new HumanoidBody<>(new ZombieModel<>(root));
        //?} else {
        /*return new HumanoidBody<>(new HumanoidModel<T>(root) {
            @Override
            public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                  float netHeadYaw, float headPitch) {
                super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
                AnimationUtils.animateZombieArms(this.leftArm, this.rightArm, entity.isAggressive(),
                        this.attackTime, ageInTicks);
            }
        });
        *///?}
    }
}
