package dev.golem.core.gui;

//? if <26.1 {
/*import net.minecraft.client.gui.screens.Screen;

/^*
 * Stands in for 26.1's {@code KeyEvent} on versions that pass loose key codes.
 *
 * <p>Counterpart to {@link MouseInput}: 26.1 bundles {@code (key, scanCode, modifiers)} into a record,
 * older versions pass them separately. Accessor names match vanilla's so screen bodies are identical
 * across versions.
 ^/
public record KeyInput(int key, int scanCode, int modifiers) {

    /^* Whether shift was held, matching the vanilla event's convenience accessor. ^/
    public boolean hasShiftDown() {
        return Screen.hasShiftDown();
    }
}
*///?}
