package dev.golem.core.flywheel;

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
 *       introduced with 26.1, and it needs an access transformer plus an interface injection that only
 *       NeoForge can supply — so it compiles only on 26.1 NeoForge. This class does not: it is present
 *       everywhere with inert bodies, so a consumer calls {@link #init} unconditionally and asks
 *       {@link #isAvailable} when the answer actually changes what it draws.</li>
 *   <li><b>How it starts.</b> CE registers thirteen listeners across two buses by hand at its own
 *       client-init site. Which bus each one belongs on is a property of the engine, not of the mod
 *       using it, so that wiring lives here and {@link #init} is the whole of it.</li>
 * </ul>
 *
 * <p>An available engine still draws nothing until visuals are registered against it; it is an
 * addition to the vanilla block-entity renderer path, never a replacement for it.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class Flywheel {

    private static @Nullable CrumblingCoreResolver crumblingCoreResolver;
    private static boolean initialized;

    private Flywheel() {}

    /** Whether this node has an engine at all. False everywhere but 26.1 NeoForge. */
    public static boolean isAvailable() {
        //? if neoforge && >=26.1 {
        return true;
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
     * <p>Note that this engine has no player-facing off switch — {@code BackendManager} picks the best
     * supported backend or fails — so unlike the published Flywheel there is no "installed but turned
     * off" state to detect here.
     */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        //? if neoforge && >=26.1 {
        return level != null
                && dev.golem.core.flywheel.visual.VisualizationLifecycle.get(level) != null;
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
        if (initialized) return;
        initialized = true;
        dev.golem.core.flywheel.backend.Backends.init();
        // Pipelines are a registry event on the mod bus; everything else is a game-bus event.
        net.neoforged.bus.api.IEventBus modBus = dev.golem.core.platform.NeoForgeEntrypoint.modBus();
        modBus.addListener(Pipelines::register);
        modBus.addListener(dev.golem.core.flywheel.indirect.IndirectPipelines::register);

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.GolemRenderSystem::onClientTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.GolemRenderSystem::onRenderFramePre);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Engine::onRenderOpaque);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Engine::onRenderTranslucent);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Engine::onRenderTranslucentFabulous);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Engine::onRenderCrumbling);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(Engine::onResourceLoadFinished);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.VisualizationLifecycle::onLevelLoad);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.VisualizationLifecycle::onLevelUnload);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.VisualizationLifecycle::onEntityJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                dev.golem.core.flywheel.visual.VisualizationLifecycle::onEntityLeave);
        //?}
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
