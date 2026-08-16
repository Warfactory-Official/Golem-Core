package dev.golem.core.gui;

//? if >=26.1 {
import net.minecraft.client.input.KeyEvent;
//?} else {
/*import net.minecraft.client.gui.screens.Screen;
*///?}

/** A version-neutral key event for compatibility-screen hooks. */
public final class KeyInput {

    private final int key;
    private final int scanCode;
    private final int modifiers;
    private final boolean shiftDown;

    //? if >=26.1 {
    private final KeyEvent event;
    //?}

    //? if >=26.1 {
    public KeyInput(KeyEvent event) {
        this.key = event.key();
        this.scanCode = event.scancode();
        this.modifiers = event.modifiers();
        this.shiftDown = event.hasShiftDown();
        this.event = event;
    }
    //?} else {
    /*public KeyInput(int key, int scanCode, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
        this.shiftDown = Screen.hasShiftDown();
    }
    *///?}

    public int key() { return this.key; }
    public int scanCode() { return this.scanCode; }
    public int modifiers() { return this.modifiers; }
    public boolean hasShiftDown() { return this.shiftDown; }

    /** Matches a configurable vanilla key binding without exposing version-specific events. */
    public boolean matches(net.minecraft.client.KeyMapping mapping) {
        //? if >=26.1 {
        return mapping.matches(this.event);
        //?} else {
        /*return mapping.matches(this.key, this.scanCode);
        *///?}
    }

    /** Delivers this input to a text widget without exposing its version-specific event type. */
    public boolean keyPressed(net.minecraft.client.gui.components.EditBox editBox) {
        //? if >=26.1 {
        return editBox.keyPressed(this.event);
        //?} else {
        /*return editBox.keyPressed(this.key, this.scanCode, this.modifiers);
        *///?}
    }
}
