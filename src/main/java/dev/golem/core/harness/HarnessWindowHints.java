package dev.golem.core.harness;

/** Public entry points for the harness mixins (mixin classes live in another package). */
public final class HarnessWindowHints {

    private HarnessWindowHints() {}

    public static void apply() {
        HarnessWindow.applyCreationHints();
    }

    public static boolean blockMouseGrab() {
        return HarnessWindow.blockMouseGrab();
    }
}
