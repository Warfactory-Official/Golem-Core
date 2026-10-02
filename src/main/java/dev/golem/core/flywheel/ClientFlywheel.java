package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.flywheel.visual.CrumblingCoreResolver;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * The GPU instancing engine: one entry point for the whole of {@code dev.golem.core.flywheel}.
 *
 * <p>Engine Room has a 26.1 source branch but no published artifact used by this build, so this package
 * vendors the engine rather than binding to {@code dev.engine_room}. Every consumer-facing decision
 * about it is made here:
 *
 * <ul>
 *   <li><b>Where it exists.</b> The engine is written against the blaze3d GPU-buffer and pipeline API
 *       introduced with 26.1, so it compiles on every 26.1 node and on none below. It needs two
 *       {@code GlBuffer} fields widened, which NeoForge supplies as an access transformer and Fabric
 *       as an access widener. This class is not gated at all: it is present everywhere with inert
 *       bodies, so a consumer calls {@link #init} unconditionally and asks {@link #isAvailable} when
 *       the answer actually changes what it draws.</li>
 *   <li><b>How it starts.</b> CE registers thirteen listeners across two buses by hand at its own
 *       client-init site. Which listener a loader spells where is a property of the engine, not of
 *       the mod using it, so that wiring lives here and {@link #init} is the whole of it.</li>
 * </ul>
 *
 * <p>An available engine still draws nothing until visuals are registered against it; it is an
 * addition to the vanilla block-entity renderer path, never a replacement for it.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class ClientFlywheel {

    private static @Nullable CrumblingCoreResolver crumblingCoreResolver;
    private static boolean initialized;

    /**
     * Whether the backend setting asked for no engine at all when the game started.
     *
     * <p>The published Flywheel is a mod with a backend the player can switch off, and every consumer
     * is written to survive that. The vendored engine now has the same switch —
     * {@code config/golemcore-flywheel.json} or {@code /flywheel backend off} (see
     * {@link FlywheelConfig}) — which also puts a scene side by side with itself: an instanced draw that
     * is subtly wrong looks like a correct one until you have that comparison.
     */
    private static final boolean DISABLED = Flywheel.disabled();

    /**
     * The requested backend, normalised, or an empty string. Delegates to {@link Flywheel}, which reads
     * the JVM switch, then the environment — a dev client launched through Loom takes its JVM arguments
     * from the buildscript, so the property alone would be unreachable from the one place it is most
     * wanted — then the player's config file.
     */
    public static String backendSetting() {
        return Flywheel.backendSetting();
    }

    private ClientFlywheel() {}

    /** Whether this node has an engine at all. False on every node below 26.1. */
    public static boolean isAvailable() {
        //? if >=26.1 {
        return !DISABLED;
        //?} else {
        /*return false;
        *///?}
    }

    /**
     * Whether the engine is actually drawing {@code level} right now: it exists, it has started, and a
     * visualization manager has been created for that level.
     *
     * <p>Narrower than {@link #isAvailable} and the one to ask before choosing an instanced path over a
     * vanilla renderer. It is false for a server level and false before a client level has loaded.
     *
     * <p>False as well whenever {@link EngineState} has the engine off — switched off by the player at
     * runtime, or stopped by a failure — because no manager exists for any level then.
     */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        //? if >=26.1 {
        // Check the physical level before naming VisualizationLifecycle. That type owns the client
        // lifecycle and therefore resolves ClientLevel during Fabric's environment stripping. A
        // dedicated-server level must short-circuit while this method still has only common symbols
        // in play; otherwise the supposedly inert server query fails during class resolution.
        if (level == null || !level.isClientSide()) return false;
        return dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level) != null;
        //?} else {
        /*return false;
        *///?}
    }

    /**
     * Subscribes the engine to the events that drive it. Safe to call on any node — where there is no
     * engine this does nothing.
     */
    public static void init() {
        //? if neoforge && >=26.1 {
        FlywheelConfig.writeDefaultIfMissing();
        FlywheelCommands.register();
        if (initialized || DISABLED) return;
        initialized = true;
        dev.golem.core.flywheel.backend.Backends.init();
        dev.golem.core.client.ClientTicks.onEndTick(
                dev.golem.core.flywheel.visual.GolemRenderSystem::onClientTick);

        // Pipelines are a registry event on the mod bus; everything else is a game-bus event. A
        // pipeline that fails to declare stops the engine, never the game.
        net.neoforged.bus.api.IEventBus modBus = dev.golem.core.platform.NeoForgeEntrypoint.modBus();
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) ->
                EngineState.guard("pipeline registration", () -> Pipelines.register(event)));
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent event) ->
                EngineState.guard("indirect pipeline registration",
                        () -> dev.golem.core.flywheel.indirect.IndirectPipelines.register(event)));

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event) ->
                        dev.golem.core.flywheel.visual.GolemRenderSystem.onFrameStart(
                                event.getPartialTick().getGameTimeDeltaPartialTick(false)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterOpaqueFeatures event) ->
                        Engine.drawOpaque(event.getModelViewMatrix()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterWeather event) ->
                        Engine.drawTranslucent(event.getModelViewMatrix()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderLevelStageEvent.AfterTranslucentBlocks event) -> {
                    Engine.drawTranslucentFabulous(event.getModelViewMatrix());
                    Engine.drawCrumbling(event.getModelViewMatrix(), event.getLevelRenderState());
                });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent event) ->
                        Engine.onResourceReload());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.LevelEvent.Load event) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onLevelLoad(event.getLevel()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.level.LevelEvent.Unload event) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onLevelUnload(event.getLevel()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onEntityJoin(
                                event.getLevel(), event.getEntity()));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onEntityLeave(
                                event.getLevel(), event.getEntity()));
        //?} elif fabric && >=26.1 {
        /*FlywheelConfig.writeDefaultIfMissing();
        FlywheelCommands.register();
        if (initialized || DISABLED) return;
        initialized = true;
        dev.golem.core.flywheel.backend.Backends.init();
        dev.golem.core.client.ClientTicks.onEndTick(
                dev.golem.core.flywheel.visual.GolemRenderSystem::onClientTick);
        dev.golem.core.client.ClientResourceReloads.register(
                dev.golem.core.GolemCore.MOD_ID, "flywheel_meshes", Engine::onResourceReload);

        // Fabric has no pipeline registry to declare into, and a pipeline vanilla was never told
        // about still compiles on first bind, so Pipelines builds itself from the first draw.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_EXTRACTION.register(
                context -> dev.golem.core.flywheel.visual.GolemRenderSystem.onFrameStart(
                        context.deltaTracker().getGameTimeDeltaPartialTick(false)));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_SOLID_FEATURES.register(
                context -> Engine.drawOpaque(Engine.cameraModelView()));
        // Deviation: NeoForge draws the non-Fabulous translucent pass after weather, and Fabric
        // offers no hook inside the weather pass at all. The end of the main pass is the last one
        // it does offer, so on Fabric clouds and weather composite over these particles instead of
        // under them. Everything else about the pass — depth state, what has already drawn into
        // main — is the same at both points.
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(
                context -> Engine.drawTranslucent(Engine.cameraModelView()));
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(
                context -> {
                    org.joml.Matrix4fc modelView = Engine.cameraModelView();
                    Engine.drawTranslucentFabulous(modelView);
                    Engine.drawCrumbling(modelView, context.levelState());
                });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register(
                (client, level) -> {
                    dev.golem.core.flywheel.visual.VisualizationLifecycle.retainOnly(level);
                    dev.golem.core.flywheel.visual.VisualizationLifecycle.onLevelLoad(level);
                });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.retainOnly(null));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register(
                (entity, level) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onEntityJoin(level, entity));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_UNLOAD.register(
                (entity, level) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onEntityLeave(level, entity));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register(
                (blockEntity, level) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onBlockEntityAdded(level, blockEntity));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(
                (blockEntity, level) ->
                        dev.golem.core.flywheel.visual.VisualizationLifecycle.onBlockEntityRemoved(
                                level, blockEntity.getBlockPos()));
        *///?}
    }

    /** Whether the engine subscribed its listeners at startup, which a runtime switch-on needs. */
    public static boolean initialized() {
        return initialized;
    }

    /**
     * Installs the hook that maps a mined multiblock dummy to the block that owns its visual, replacing
     * CE's inline {@code BlockDummyable} test. See {@link CrumblingCoreResolver}.
     */
    public static void setCrumblingCoreResolver(@Nullable CrumblingCoreResolver resolver) {
        crumblingCoreResolver = resolver;
    }

    static @Nullable CrumblingCoreResolver crumblingCoreResolver() {
        return crumblingCoreResolver;
    }
}
//?}
