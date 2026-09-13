package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.instance.InstancedDrawManager;

// Mirrors upstream Flywheel's Backends. Capability-gated INDIRECT has priority 1000, with universally
// supported INSTANCING at priority 500 as its fallback. BATCHING was removed upstream in Dec 2023.
public final class Backends {

    public static final Backend INSTANCING = new Backend() {
        @Override
        public String name() {
            return "instancing";
        }

        @Override
        public int priority() {
            return 500;
        }

        @Override
        public boolean supported() {
            return true;
        }

        @Override
        public DrawManager createDrawManager() {
            return new InstancedDrawManager();
        }
    };

    public static final IndirectBackend INDIRECT = IndirectBackend.INSTANCE;

    private Backends() {
    }

    public static void init() {
        BackendManager.register(INSTANCING);
        BackendManager.register(INDIRECT);
    }
}
//?}
