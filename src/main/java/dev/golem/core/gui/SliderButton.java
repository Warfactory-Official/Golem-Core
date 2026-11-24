package dev.golem.core.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A slider with a version-neutral release hook; 1.21.11 changed {@code onRelease} to take a mouse event. */
public abstract class SliderButton extends AbstractSliderButton {

    protected SliderButton(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    /** The mouse button was let go after a click or drag on this slider. */
    protected void onReleased() {}

    //? if >=1.21.11 {
    @Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        super.onRelease(event);
        onReleased();
    }
    //?} else {
    /*@Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        onReleased();
    }
    *///?}
}
