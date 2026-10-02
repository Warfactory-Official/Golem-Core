package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The engine's one off switch. A throw from any guarded entry point must stop the engine — never
 * escape into the loader's event bus — and every later entry point must stand down, which is what
 * makes {@code Visuals.owns} answer no and every vanilla renderer draw again.
 */
final class EngineStateTest {

    @AfterEach
    void reset() {
        EngineState.resetForTest();
    }

    @Test
    void aThrowSwitchesTheEngineOffOnceAndDoesNotEscape() {
        assertTrue(EngineState.running());
        EngineState.guard("frame start", () -> {
            throw new IllegalStateException("boom");
        });
        assertFalse(EngineState.running());
        assertTrue(EngineState.failed());
        assertNotNull(EngineState.offReason());
        assertTrue(EngineState.takeTeardown(), "the failure must schedule exactly one teardown");
        assertFalse(EngineState.takeTeardown());

        AtomicInteger ran = new AtomicInteger();
        EngineState.guard("opaque draw", ran::incrementAndGet);
        assertEquals(0, ran.get(), "nothing may run after the engine failed");
    }

    @Test
    void linkageErrorsAreAbsorbedToo() {
        EngineState.guard("pipeline registration", () -> {
            throw new NoSuchFieldError("persistentBuffer");
        });
        assertTrue(EngineState.failed());
    }

    @Test
    void unsupportedDeviceIsAnEngineFailure() {
        EngineState.guard("slab", () -> {
            throw new EngineState.Unsupported("no persistent mapping");
        });
        assertTrue(EngineState.failed());
    }

    @Test
    void switchOffIsReversibleButAFailureIsNot() {
        EngineState.switchOff("switched off with /flywheel backend off");
        assertFalse(EngineState.running());
        assertFalse(EngineState.failed());
        assertTrue(EngineState.switchOn());
        assertTrue(EngineState.running());

        EngineState.fail("draw", new RuntimeException("x"));
        assertFalse(EngineState.switchOn(), "a failed engine stays off for the session");
        assertFalse(EngineState.running());
    }

    @Test
    void switchOnCancelsAnUnclaimedTeardown() {
        // off and on inside one tick: the teardown scheduled by the switch-off must not reach the manager the
        // switch-on is about to build
        EngineState.switchOff("switched off with /flywheel backend off");
        assertTrue(EngineState.switchOn());
        assertFalse(EngineState.takeTeardown(), "a switch-on must drop the pending teardown");
        assertTrue(EngineState.running());

        EngineState.fail("draw", new RuntimeException("x"));
        assertFalse(EngineState.switchOn());
        assertTrue(EngineState.takeTeardown(), "a failed engine keeps its teardown");
        assertFalse(EngineState.running());
    }
}
//?}
