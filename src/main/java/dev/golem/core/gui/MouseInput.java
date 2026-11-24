package dev.golem.core.gui;

//? if >=1.21.11 {
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * A version-neutral mouse button event for {@link CompatContainerScreen} hooks. {@code button} counts from 0 (left,
 * right, middle) on every node, as GLFW does; 26.3 reads input through SDL, which counts from 1.
 */
public record MouseInput(double x, double y, int button, boolean shiftDown) {

    //? if >=1.21.11 {
    public MouseInput(MouseButtonEvent event) {
        this(event.x(), event.y(), fromNode(event.button()), event.hasShiftDown());
    }

    private static int fromNode(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) return 2;
        return button;
    }
    //?} else {
    /*public MouseInput(double x, double y, int button) {
        this(x, y, button, net.minecraft.client.gui.screens.Screen.hasShiftDown());
    }
    *///?}

    public boolean hasShiftDown() {
        return this.shiftDown;
    }
}
