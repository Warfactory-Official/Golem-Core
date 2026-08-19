package dev.golem.core.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;

/**
 * Attaches a {@link CompatHumanoidLayer} to every humanoid renderer the game has.
 *
 * <p>Three things differ across the matrix and all three are absorbed here, so a mod declares its layer
 * once with no guard of its own.
 *
 * <ul>
 * <li><b>The hook.</b> NeoForge and Forge only accept layers inside a mod-bus {@code AddLayers} event,
 *     so registration is deferred onto that bus exactly as {@code KeyMappings} defers key bindings.
 *     fabric-api instead fires a callback per living renderer as the renderers are built.
 * <li><b>The fabric-api entry point was renamed.</b> Through 1.21.1 it is
 *     {@code LivingEntityFeatureRendererRegistrationCallback}; the 26.x line dropped that class and
 *     replaced it with {@code LivingEntityRenderLayerRegistrationCallback}. Same four arguments, same
 *     helper, different name — a straight rename, not a behaviour change.
 * <li><b>Player renderers are not in the entity-type map</b> on any loader, so they have to be walked
 *     separately — and that side keyed its map differently three times: {@code String} on Forge 1.20.1,
 *     {@code PlayerSkin.Model} on NeoForge 1.21.1, {@code PlayerModelType} on 26.1, which also split
 *     mannequins into a map of their own.
 * </ul>
 *
 * <p>The entity types themselves are walked from {@code BuiltInRegistries.ENTITY_TYPE} rather than from
 * the event, because Forge 1.20.1's {@code AddLayers} exposes no {@code getEntityTypes()} — the registry
 * is the one enumeration all six nodes agree on.
 *
 * <p>Only renderers whose model is a {@link HumanoidModel} are offered to the factory — a layer posing
 * against {@link HumanoidPose} has nothing to attach itself to otherwise.
 *
 * <p>Client-only: never call this from common initialization.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class HumanoidLayers {

    private HumanoidLayers() {}

    /** Builds the layer for one humanoid renderer. Called once per renderer, at renderer-build time. */
    @FunctionalInterface
    public interface Factory {
        CompatHumanoidLayer create(RenderLayerParent parent);
    }

    /**
     * Declares a layer to be attached to every humanoid renderer, players included.
     *
     * <p>Call during client initialization. On the mod-bus loaders this must happen while the mod is
     * being constructed, which is where the client init hook already runs.
     */
    public static void register(Factory factory) {
        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT
                .register((entityType, renderer, helper, context) -> {
                    CompatHumanoidLayer layer = build(renderer, factory);
                    if (layer == null) return;
                    helper.register(layer);
                    attached++;
                });
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT
                .register((entityType, renderer, helper, context) -> {
                    CompatHumanoidLayer layer = build(renderer, factory);
                    if (layer == null) return;
                    helper.register(layer);
                    attached++;
                });
        *///?} elif neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) -> {
                    for (net.minecraft.world.entity.player.PlayerModelType skin : event.getSkins()) {
                        attach(event.getPlayerRenderer(skin), factory);
                        // Mannequins are avatars too and got their own map at 26.1; a suit of armour on
                        // a display stand should look like a suit of armour on a player.
                        attach(event.getMannequinRenderer(skin), factory);
                    }
                    for (net.minecraft.world.entity.EntityType<?> type
                            : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                        attach(event.getRenderer((net.minecraft.world.entity.EntityType) type), factory);
                    }
                    report();
                });
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) -> {
                    for (net.minecraft.client.resources.PlayerSkin.Model skin : event.getSkins()) {
                        attach(event.getSkin(skin), factory);
                    }
                    for (net.minecraft.world.entity.EntityType<?> type
                            : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                        attach(event.getRenderer((net.minecraft.world.entity.EntityType) type), factory);
                    }
                    report();
                });
        *///?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event) -> {
                    for (String skin : event.getSkins()) {
                        attach(event.getSkin(skin), factory);
                    }
                    for (net.minecraft.world.entity.EntityType<?> type
                            : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE) {
                        attach(rendererOrNull(event, type), factory);
                    }
                    report();
                });
        *///?}
    }

    /**
     * How many renderers have actually received a layer, across every {@link #register} call.
     *
     * <p>This is logged rather than left implicit because "no error appeared" says nothing here: a
     * layer attached to zero renderers — the wrong event, a renderer map that was empty at the time,
     * a model that turned out not to be humanoid — draws nothing and throws nothing. The count is the
     * positive signal to read in the client log. Expect a number in the low tens on a vanilla game.
     *
     * <p>It is exposed rather than logged here because the loaders differ on when a batch <i>ends</i>:
     * the mod-bus event hands over every renderer at once, while fabric-api fires its callback once per
     * renderer with no completion signal. The caller logs it when it has somewhere sensible to.
     */
    private static int attached;

    /** How many humanoid renderers have received a layer so far. See {@link #attached}. */
    public static int attachedCount() {
        return attached;
    }

    //? if neoforge || forge {
    /**
     * Logs the running total once the mod-bus event has handed over every renderer.
     *
     * <p>Only the mod-bus loaders can do this: fabric-api fires its callback once per renderer with no
     * completion signal, so there the number has to be read through {@link #attachedCount()} instead.
     */
    private static void report() {
        dev.golem.core.GolemCore.LOG.info("Humanoid render layers attached: {}", attached);
    }
    //?}

    //? if neoforge || forge {
    /**
     * Adds the layer to {@code renderer} if it is a humanoid one. Null and non-living are no-ops.
     *
     * <p>Only the mod-bus loaders need this. {@code LivingEntityRenderer#addLayer} is {@code protected}
     * in vanilla and made {@code public} by the Forge/NeoForge patches, so on Fabric it is unreachable
     * from here — which is fine, because there the registration helper fabric-api hands to the callback
     * does the attaching itself. Calling it unconditionally is what made all three Fabric nodes fail to
     * compile while all three patched ones passed.
     */
    //? if forge {
    /*/^*
     * Forge 1.20.1's {@code getRenderer} is not safe to call for an arbitrary entity type: its body is
     * a bare {@code checkcast} to {@code LivingEntityRenderer} on the map lookup, so asking it about
     * any type whose renderer is not one — {@code minecraft:marker} and friends get a {@code
     * NoopRenderer} — throws {@code ClassCastException} from inside Forge, before {@link #build}'s
     * {@code instanceof} guard ever sees the value.
     *
     * <p>There is no way to ask first: the renderer map is private and the event exposes no
     * {@code getEntityTypes()}. So the cast failure IS the test — it means precisely "not a living
     * renderer", which is the set we want to skip. NeoForge returns the value without casting, and
     * fabric-api hands over only living renderers, so neither needs this.
     ^/
    private static Object rendererOrNull(net.minecraftforge.client.event.EntityRenderersEvent.AddLayers event,
                                         net.minecraft.world.entity.EntityType<?> type) {
        try {
            return event.getRenderer((net.minecraft.world.entity.EntityType) type);
        } catch (ClassCastException notLiving) {
            return null;
        }
    }
    *///?}

    private static void attach(Object renderer, Factory factory) {
        CompatHumanoidLayer layer = build(renderer, factory);
        if (layer == null) return;
        ((LivingEntityRenderer) renderer).addLayer(layer);
        attached++;
    }
    //?}


    /**
     * The layer for {@code renderer}, or null if it is not a humanoid renderer.
     *
     * <p>{@code LivingEntityRenderer}'s arity changed at 26.1 (a render-state parameter was inserted),
     * so it is named raw here; the only member touched is {@code getModel}, whose erasure is stable.
     */
    private static CompatHumanoidLayer build(Object renderer, Factory factory) {
        if (!(renderer instanceof LivingEntityRenderer living)) return null;
        if (!(living.getModel() instanceof HumanoidModel<?>)) return null;
        return factory.create(living);
    }
}
