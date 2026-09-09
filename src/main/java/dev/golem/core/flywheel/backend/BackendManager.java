package dev.golem.core.flywheel.backend;

//? if neoforge && >=26.1 {

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.List;

public final class BackendManager {

    private static final List<Backend> REGISTERED = new ArrayList<>();
    private static Backend current;

    private BackendManager() {
    }

    public static void register(Backend backend) {
        REGISTERED.add(backend);
    }

    public static Backend current() {
        Backend snapshot = current;
        if (snapshot != null) return snapshot;
        String forced = System.getProperty("golem.flywheel.backend");
        if (forced != null && !forced.isEmpty()) {
            for (Backend b : REGISTERED) {
                if (!b.name().equalsIgnoreCase(forced)) continue;
                if (!b.supported()) {
                    throw new IllegalStateException("Golem flywheel backend '" + forced + "' was forced via -Dgolem.flywheel.backend but reports unsupported on this system");
                }
                current = b;
                GolemCore.LOG.info("Golem flywheel backend forced via -Dgolem.flywheel.backend: {}", b.name());
                return b;
            }
            throw new IllegalStateException("Unknown Golem flywheel backend forced via -Dgolem.flywheel.backend: " + forced);
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
