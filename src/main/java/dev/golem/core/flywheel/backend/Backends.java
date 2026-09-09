package dev.golem.core.flywheel.backend;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.DrawManager;
import dev.golem.core.flywheel.instance.InstancedDrawManager;

// Mirrors upstream Flywheel's Backends. INSTANCING (priority 500) is the working backend;
// INDIRECT (priority 1000) is registered but force-disabled via supported()=false until the
// SSBO draw manager + compute culler land (slices 2c-2 through 2c-4). BATCHING was removed
// upstream in Dec 2023 ("Gone, reduced to batches", 2dbdf83e); we never add it.
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
