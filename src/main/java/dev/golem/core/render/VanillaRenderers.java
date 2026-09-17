package dev.golem.core.render;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.world.entity.Entity;

/**
 * Vanilla entity renderers handed out for a mod's own entity types.
 *
 * <p>Vanilla types these renderers as {@code EntityRenderer<AbstractMinecart>}, {@code <PrimedTnt>}
 * and so on, which is the wrong shape for a registration keyed by a subclass's {@code EntityType} —
 * and on 26.1 {@code MinecartRenderer} lost its type parameter altogether. Each factory here erases
 * to the caller's entity type, which is sound because the renderer only ever receives entities of the
 * type it was registered for.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class VanillaRenderers {

    private VanillaRenderers() {}

    /** The plain minecart body; the cart's display block, if any, rides inside it. */
    public static <T extends Entity> EntityRendererProvider<T> minecart() {
        return context -> (net.minecraft.client.renderer.entity.EntityRenderer) new MinecartRenderer(context, ModelLayers.MINECART);
    }

    /** The primed-TNT block, flashing as its fuse runs down. */
    public static <T extends Entity> EntityRendererProvider<T> tnt() {
        return context -> (net.minecraft.client.renderer.entity.EntityRenderer) new TntRenderer(context);
    }

    /** A dropped item stack, bobbing and spinning. */
    public static <T extends Entity> EntityRendererProvider<T> item() {
        return context -> (net.minecraft.client.renderer.entity.EntityRenderer) new ItemEntityRenderer(context);
    }

    /** Draws nothing — for logic entities, which still need a renderer registered or 26.1 crashes on them. */
    public static <T extends Entity> EntityRendererProvider<T> noop() {
        return NoopRenderer::new;
    }
}
