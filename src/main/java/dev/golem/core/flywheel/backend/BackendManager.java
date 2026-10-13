package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.FlywheelConfig;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Chooses the backend, and never throws doing it.
 *
 * <p>This runs from the client level-load listener, where a throw (an unknown name in the config, a
 * forced backend the driver cannot do) would take the whole client down for a mere acceleration. Like
 * the published engine, a bad setting is a warning and the automatic choice, and "nothing supported" is
 * {@code null}, which the caller treats as "draw everything through the vanilla renderers".
 */
public final class BackendManager {

    private static final List<Backend> REGISTERED = new ArrayList<>();
    private static @Nullable Backend current;
    private static boolean chosen;

    private BackendManager() {
    }

    public static void register(Backend backend) {
        REGISTERED.add(backend);
    }

    /** The backend already chosen, or null before the first level forced a choice or when none was usable. */
    public static @Nullable Backend selected() {
        return current;
    }

    /** Forgets the choice so the next level makes it again from the current setting. */
    public static synchronized void reset() {
        current = null;
        chosen = false;
    }

    /** The backend to draw with, or null when the setting is off or no registered backend is supported. */
    public static synchronized @Nullable Backend current() {
        if (chosen) return current;
        current = choose(REGISTERED, dev.golem.core.flywheel.Flywheel.backendSetting());
        chosen = true;
        return current;
    }

    /** The choice itself, free of static state so it can be tested without a GL context. */
    static @Nullable Backend choose(List<Backend> registered, String setting) {
        String forced = FlywheelConfig.normalise(setting);
        if (FlywheelConfig.isOff(forced)) return null;
        if (!forced.isEmpty()) {
            Backend named = null;
            for (Backend b : registered) {
                if (b.name().equalsIgnoreCase(forced)) named = b;
            }
            if (named == null) {
                GolemCore.LOG.warn("Unknown Golem flywheel backend '{}' (config {} / golem.flywheel.backend / "
                        + "GOLEM_FLYWHEEL_BACKEND); choosing automatically", forced, FlywheelConfig.FILE_NAME);
            } else if (supported(named)) {
                GolemCore.LOG.info("Golem flywheel backend forced: {}", named.name());
                return named;
            } else {
                GolemCore.LOG.warn("Golem flywheel backend '{}' was requested but is not supported on this "
                        + "system; choosing automatically", named.name());
            }
        }
        Backend best = null;
        for (Backend b : registered) {
            if (!supported(b)) continue;
            if (best == null || b.priority() > best.priority()) best = b;
        }
        if (best == null) {
            GolemCore.LOG.warn("No Golem flywheel backend is supported on this system (registered={}); "
                    + "every machine draws through its vanilla renderer", registered.size());
            return null;
        }
        GolemCore.LOG.info("Golem flywheel backend selected: {} (priority {})", best.name(), best.priority());
        return best;
    }

    /** A capability probe that throws is a backend that is not supported, not a crash. */
    private static boolean supported(Backend backend) {
        try {
            return backend.supported();
        } catch (RuntimeException | LinkageError failure) {
            GolemCore.LOG.warn("Golem flywheel backend '{}' failed its capability check; treating it as unsupported",
                    backend.name(), failure);
            return false;
        }
    }
}
//?}
