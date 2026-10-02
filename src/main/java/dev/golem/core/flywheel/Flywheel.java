package dev.golem.core.flywheel;

import dev.golem.core.flywheel.visual.CrumblingCoreResolver;
import net.minecraft.world.level.LevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Common facade for the optional GPU-instancing engine.
 *
 * <p>The implementation is kept behind the client boundary in {@link ClientFlywheel}. Fabric's
 * dedicated-server loader verifies method bodies eagerly, so this class must contain only common
 * symbols on the path used by server-side capability checks.
 */
public final class Flywheel {

    private static @Nullable CrumblingCoreResolver crumblingCoreResolver;

    /**
     * Whether the backend setting asked for no engine at all when the game started. Read once: the
     * engine's pipelines are registered at startup, so an engine that was off then cannot be started
     * later without a restart. Switching off at runtime goes through {@code EngineState} instead.
     */
    private static final boolean DISABLED = FlywheelConfig.isOff(backendSetting());

    private Flywheel() {}

    /**
     * The requested backend, normalised by {@link FlywheelConfig#normalise}, or an empty string for the
     * default. {@code -Dgolem.flywheel.backend} wins, then {@code GOLEM_FLYWHEEL_BACKEND}, then the
     * player's {@code config/golemcore-flywheel.json}.
     */
    public static String backendSetting() {
        String forced = System.getProperty("golem.flywheel.backend");
        if (forced == null || forced.isEmpty()) forced = System.getenv("GOLEM_FLYWHEEL_BACKEND");
        if (forced != null && !forced.isEmpty()) return FlywheelConfig.normalise(forced);
        return FlywheelConfig.backend();
    }

    /** Whether the backend comes from the JVM switch or the environment rather than the config file. */
    public static boolean backendForcedExternally() {
        String forced = System.getProperty("golem.flywheel.backend");
        if (forced == null || forced.isEmpty()) forced = System.getenv("GOLEM_FLYWHEEL_BACKEND");
        return forced != null && !forced.isEmpty();
    }

    /** Whether this node has an engine at all. False on every node below 26.1. */
    public static boolean isAvailable() {
        //? if >=26.1 {
        return !DISABLED;
        //?} else {
        /*return false;
        *///?}
    }

    /** Whether the engine is actually drawing {@code level} right now. */
    public static boolean isVisualizing(@Nullable LevelAccessor level) {
        //? if >=26.1 {
        if (level == null || !level.isClientSide()
                || !dev.golem.core.platform.Platform.isClient()) return false;
        return Client.isVisualizing(level);
        //?} else {
        /*return false;
        *///?}
    }

    /**
     * Subscribes the engine to client events. Safe to call from common code. Called even when the
     * backend is off, so the {@code /flywheel} command and the config file exist to turn it back on;
     * the client half subscribes nothing else in that case.
     */
    public static void init() {
        //? if >=26.1 {
        if (!dev.golem.core.platform.Platform.isClient()) return;
        Client.init();
        //?}
    }

    /** Installs the content-owned multiblock crumbling resolver. */
    public static void setCrumblingCoreResolver(@Nullable CrumblingCoreResolver resolver) {
        crumblingCoreResolver = resolver;
    }

    static @Nullable CrumblingCoreResolver crumblingCoreResolver() {
        return crumblingCoreResolver;
    }

    /** Package-private bridge for the client implementation's shared disabled setting. */
    static boolean disabled() {
        return DISABLED;
    }

    //? if >=26.1 {
    private static final class Client {

        private Client() {}

        private static boolean isVisualizing(LevelAccessor level) {
            return ClientFlywheel.isVisualizing(level);
        }

        private static void init() {
            ClientFlywheel.init();
        }
    }
    //?}
}
