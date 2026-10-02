package dev.golem.core.flywheel.backend;

//? if neoforge && >=26.1 {

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.golem.core.flywheel.DrawManager;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The backend choice must never throw: it runs from the client level-load listener, where a throw is a
 * crash for a feature that is only an acceleration. Every bad setting falls back like the published
 * engine's config does, and "nothing usable" is null — no visualization, vanilla renderers draw.
 */
final class BackendManagerTest {

    private static Backend fake(String name, int priority, boolean supported) {
        return new Backend() {
            @Override public String name() { return name; }
            @Override public int priority() { return priority; }
            @Override public boolean supported() { return supported; }
            @Override public DrawManager createDrawManager() { throw new AssertionError("not drawn in a test"); }
        };
    }

    private static Backend throwing(String name, int priority) {
        return new Backend() {
            @Override public String name() { return name; }
            @Override public int priority() { return priority; }
            @Override public boolean supported() { throw new IllegalStateException("no GL context"); }
            @Override public DrawManager createDrawManager() { throw new AssertionError("not drawn in a test"); }
        };
    }

    private final Backend instancing = fake("instancing", 500, true);
    private final Backend indirect = fake("indirect", 1000, false);

    @Test
    void defaultPicksTheBestSupported() {
        assertSame(instancing, BackendManager.choose(List.of(instancing, indirect), ""));
        assertSame(instancing, BackendManager.choose(List.of(instancing, indirect), "DEFAULT"));
    }

    @Test
    void offMeansNoBackend() {
        assertNull(BackendManager.choose(List.of(instancing, indirect), "off"));
        assertNull(BackendManager.choose(List.of(instancing, indirect), "flywheel:off"));
        assertNull(BackendManager.choose(List.of(instancing, indirect), "disabled"));
    }

    @Test
    void unknownNameFallsBackInsteadOfThrowing() {
        assertSame(instancing, BackendManager.choose(List.of(instancing, indirect), "batching"));
    }

    @Test
    void forcedButUnsupportedFallsBack() {
        assertSame(instancing, BackendManager.choose(List.of(instancing, indirect), "indirect"));
        assertSame(instancing, BackendManager.choose(List.of(instancing, indirect), "flywheel:indirect"));
    }

    @Test
    void nothingSupportedIsNullNotACrash() {
        Backend none = fake("instancing", 500, false);
        assertNull(BackendManager.choose(List.of(none, indirect), ""));
        assertNull(BackendManager.choose(List.of(), ""));
    }

    @Test
    void aThrowingCapabilityProbeIsUnsupported() {
        assertNull(BackendManager.choose(List.of(throwing("instancing", 500)), ""));
        assertSame(instancing, BackendManager.choose(List.of(throwing("indirect", 1000), instancing), ""));
    }
}
//?}
