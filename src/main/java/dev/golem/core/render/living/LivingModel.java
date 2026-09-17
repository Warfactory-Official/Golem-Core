package dev.golem.core.render.living;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jspecify.annotations.Nullable;

//? if >=26.1 {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.monster.blaze.BlazeModel;
import net.minecraft.client.model.monster.silverfish.SilverfishModel;
//?} else {
/*import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SilverfishModel;
*///?}

/**
 * The model a {@link CompatMobRenderer} draws, as an opaque handle.
 *
 * <p>Vanilla's model classes are generic over the entity below 26.1 and over the render state from
 * 26.1 on, so no single spelling of, say, {@code SilverfishModel<...>} compiles on every node. A mod
 * asks for the model by name and never sees the type argument; the renderer facade unwraps it against
 * whichever vanilla renderer it extends.
 *
 * @param <S> the render state the model reads
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class LivingModel<S extends CompatLivingRenderState> {

    //? if >=26.1 {
    final EntityModel<? super S> model;
    //?} else {
    /*final EntityModel model;
    *///?}
    final @Nullable CompatEntityModel<S> compat;

    private LivingModel(
            //? if >=26.1 {
            EntityModel<? super S>
            //?} else {
            /*EntityModel
            *///?}
            model, @Nullable CompatEntityModel<S> compat) {
        this.model = model;
        this.compat = compat;
    }

    /** A mod's own cube model. */
    public static <S extends CompatLivingRenderState> LivingModel<S> custom(CompatEntityModel<S> model) {
        return new LivingModel<>(model, model);
    }

    /** Vanilla's silverfish body. */
    public static <S extends CompatLivingRenderState> LivingModel<S> silverfish(EntityRendererProvider.Context context) {
        return new LivingModel<>(new SilverfishModel(context.bakeLayer(ModelLayers.SILVERFISH)), null);
    }

    /** Vanilla's blaze body: the head and its twelve orbiting rods. */
    public static <S extends CompatLivingRenderState> LivingModel<S> blaze(EntityRendererProvider.Context context) {
        return new LivingModel<>(new BlazeModel(context.bakeLayer(ModelLayers.BLAZE)), null);
    }
}
