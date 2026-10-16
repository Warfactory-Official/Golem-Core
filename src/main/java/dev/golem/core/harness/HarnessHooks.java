package dev.golem.core.harness;

import java.util.function.IntPredicate;

/**
 * Keys and mouse buttons the test harness holds. Its presses go through vanilla's handlers, which a physical poll never
 * sees, so the physical polls in {@code KeyMappings} and {@code MouseInput} ask here too. Ships in release jars, where
 * nothing installs a source and both answer false; the harness itself is stripped from them.
 */
public final class HarnessHooks {

    private static IntPredicate keys = key -> false;
    private static IntPredicate buttons = button -> false;

    private HarnessHooks() {}

    static void install(IntPredicate heldKeys, IntPredicate heldButtons) {
        keys = heldKeys;
        buttons = heldButtons;
    }

    public static boolean keyHeld(int glfwKey) {
        return keys.test(glfwKey);
    }

    public static boolean mouseHeld(int button) {
        return buttons.test(button);
    }
}
