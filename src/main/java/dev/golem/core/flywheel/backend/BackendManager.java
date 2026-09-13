package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class BackendManager {

    static final List<String> OFF = List.of("off", "none", "disabled");

    private static final List<Backend> REGISTERED = new ArrayList<>();
    private static Backend current;

    private BackendManager() {
    }

    public static void register(Backend backend) {
        REGISTERED.add(backend);
    }

    /** The backend already chosen, or null before the first level forced a choice. Never chooses one. */
    public static @Nullable Backend selected() {
        return current;
    }

    public static Backend current() {
        Backend snapshot = current;
        if (snapshot != null) return snapshot;
        String forced = dev.golem.core.flywheel.Flywheel.backendSetting();
        // "off" is handled by Flywheel.isAvailable, which stops the engine before a level ever asks
        // for a backend. Recognised here too so a forced-off run cannot die on an unknown name.
        if (!forced.isEmpty() && !OFF.contains(forced)) {
            for (Backend b : REGISTERED) {
                if (!b.name().equalsIgnoreCase(forced)) continue;
                if (!b.supported()) {
                    throw new IllegalStateException("Golem flywheel backend '" + forced + "' was forced but reports unsupported on this system");
                }
                current = b;
                GolemCore.LOG.info("Golem flywheel backend forced: {}", b.name());
                return b;
            }
            throw new IllegalStateException("Unknown Golem flywheel backend forced (golem.flywheel.backend / GOLEM_FLYWHEEL_BACKEND): " + forced);
        }
        Backend best = null;
        for (Backend b : REGISTERED) {
            if (!b.supported()) continue;
            if (best == null || b.priority() > best.priority()) best = b;
        }
        if (best == null) {
            throw new IllegalStateException("No supported Golem flywheel backend (registered=" + REGISTERED.size() + ")");
        }
        current = best;
        GolemCore.LOG.info("Golem flywheel backend selected: {} (priority {})", best.name(), best.priority());
        return best;
    }
}
//?}
