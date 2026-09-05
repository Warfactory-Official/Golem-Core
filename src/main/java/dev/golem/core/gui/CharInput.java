package dev.golem.core.gui;

//? if >=26.1 {
import net.minecraft.client.input.CharacterEvent;
//?}

/** A version-neutral character event for compatibility-screen hooks. */
public final class CharInput {

    private final char codePoint;
    private final int modifiers;

    //? if >=26.1 {
    private final CharacterEvent event;
    //?}

    //? if >=26.1 {
    public CharInput(CharacterEvent event) {
        this.codePoint = (char) event.codepoint();
        this.modifiers = 0;
        this.event = event;
    }
    //?} else {
    /*public CharInput(char codePoint, int modifiers) {
        this.codePoint = codePoint;
        this.modifiers = modifiers;
    }
    *///?}

    public char codePoint() { return this.codePoint; }
    public int modifiers() { return this.modifiers; }

    /** Delivers this character to a text widget without exposing its version-specific event type. */
    public boolean charTyped(net.minecraft.client.gui.components.EditBox editBox) {
        //? if >=26.1 {
        return editBox.charTyped(this.event);
        //?} else {
        /*return editBox.charTyped(this.codePoint, this.modifiers);
        *///?}
    }
}
