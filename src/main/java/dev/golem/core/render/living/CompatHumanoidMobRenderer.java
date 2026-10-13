package dev.golem.core.render.living;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.Mob;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * A biped mob renderer written once, on every version: vanilla's {@code HumanoidMobRenderer} with
 * its head, elytra/wings and held-item layers, plus optionally the armour layer, over one or more
 * {@link HumanoidBody bodies} each with its own skin.
 *
 * <p>A renderer with several bodies picks one per frame through {@link #variant}; the undead soldier
 * switches between a zombie and a skeleton this way. Because the renderer's model is a
 * {@code HumanoidModel}, every humanoid layer Golem attaches, including a mod's own armour renderers,
 * works here too.
 *
 * @param <T> the mob
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class CompatHumanoidMobRenderer<T extends Mob>
        //? if >=26.1 {
        extends HumanoidMobRenderer<T, CompatHumanoidRenderState, HumanoidModel<CompatHumanoidRenderState>>
        //?} else {
        /*extends HumanoidMobRenderer<T, HumanoidModel<T>>
        *///?}
{

    private final HumanoidBody<T>[] bodies;
    private final
            //? if >=26.1 {
            Identifier[]
            //?} else {
            /*ResourceLocation[]
            *///?}
            textures;

    /**
     * @param bodies   one body per variant; the first is the default
     * @param textures one skin per body
     */
    @SafeVarargs
    protected CompatHumanoidMobRenderer(EntityRendererProvider.Context context, float shadow,
                                        //? if >=26.1 {
                                        Identifier[]
                                        //?} else {
                                        /*ResourceLocation[]
                                        *///?}
                                        textures, HumanoidBody<T>... bodies) {
        super(context, bodies[0].model, shadow);
        if (bodies.length != textures.length) {
            throw new IllegalArgumentException(bodies.length + " bodies for " + textures.length + " textures");
        }
        this.bodies = bodies;
        this.textures = textures;
    }

    /** Which body and skin this entity wears now. Index into the constructor's arrays; 0 by default. */
    protected int variant(T entity) {
        return 0;
    }

    /** Adds vanilla's armour layer over the zombie armour layers, so worn equipment draws. */
    protected final void addZombieArmorLayer(EntityRendererProvider.Context context) {
        //? if >=26.1 {
        ArmorModelSet<HumanoidModel<CompatHumanoidRenderState>> armor =
                ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new);
        addLayer(new HumanoidArmorLayer<>(this, armor, context.getEquipmentRenderer()));
        //?} else {
        /*addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                context.getModelManager()));
        *///?}
    }

    //? if >=26.1 {
    @Override
    public CompatHumanoidRenderState createRenderState() {
        return new CompatHumanoidRenderState();
    }

    @Override
    public void extractRenderState(T entity, CompatHumanoidRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = variant(entity);
        state.isAggressive = entity.isAggressive();
    }

    @Override
    public void submit(CompatHumanoidRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        this.model = bodies[state.variant].model;
        super.submit(state, poseStack, collector, camera);
    }

    @Override
    public Identifier getTextureLocation(CompatHumanoidRenderState state) {
        return textures[state.variant];
    }
    //?} else {
    /*@Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        this.model = bodies[variant(entity)].model;
        super.render(entity, entityYaw, partialTicks, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return textures[variant(entity)];
    }
    *///?}
}
