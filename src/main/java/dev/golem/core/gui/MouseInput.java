package dev.golem.core.gui;

//? if >=1.21.11 {
import net.minecraft.client.input.MouseButtonEvent;
//?}

/** A version-neutral mouse button event for {@link CompatContainerScreen} hooks. */
public record MouseInput(double x, double y, int button, boolean shiftDown) {

    //? if >=1.21.11 {
    public MouseInput(MouseButtonEvent event) {
        this(event.x(), event.y(), event.button(), event.hasShiftDown());
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
