package dev.golem.core.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;

/** A labelled checkbox that reports each toggle. 1.20.1 has no {@code Checkbox.builder} or change callback. */
public final class Checkboxes {

    private Checkboxes() {}

    public static Checkbox create(Font font, int x, int y, Component label, boolean selected, Consumer<Boolean> onChange) {
        //? if >=1.21 {
        return Checkbox.builder(label, font).pos(x, y).selected(selected)
                .onValueChange((checkbox, value) -> onChange.accept(value)).build();
        //?} else {
        /*// The 1.20.1 box is 20 pixels with the label 4 pixels after it.
        return new Checkbox(x, y, 24 + font.width(label), 20, label, selected) {
            @Override
            public void onPress() {
                super.onPress();
                onChange.accept(selected());
            }
        };
        *///?}
    }
}
