package dev.golem.core.gui;

//? if <26.1 {
/*/^*
 * Stands in for 26.1's {@code MouseButtonEvent} on versions that pass loose coordinates instead.
 *
 * <p>26.1 bundled a click's position and button into one record; older versions pass
 * {@code (double mouseX, double mouseY, int button)}. Screens can keep 26.1's spelling —
 * {@code event.x()}, {@code event.y()}, {@code event.button()} — because the older branch of each
 * guarded override builds one of these from its loose arguments. The accessor names deliberately match
 * vanilla's so screen bodies need no version guards of their own.
 ^/
public record MouseInput(double x, double y, int button) {

    /^* Whether shift was held, matching the vanilla event's convenience accessor. ^/
    public boolean hasShiftDown() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }
}
*///?}
