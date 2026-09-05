package dev.golem.core.gui;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.components.EditBox;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditBoxInputTest {

    @Test
    void focusedFieldsOwnInventoryAndJeiShortcutKeysButNotEscape() {
        assertTrue(EditBoxInput.consumesKey(69)); // E / inventory
        assertTrue(EditBoxInput.consumesKey(82)); // R / JEI recipe lookup
        assertTrue(EditBoxInput.consumesKey(85)); // U / JEI usage lookup
        assertFalse(EditBoxInput.consumesKey(256)); // Escape still closes the screen
        assertFalse(EditBoxInput.consumesKey(258)); // Tab still traverses focused widgets
    }

    @Test
    void screensExposeADirectFocusedEditBoxForJeiPreKeyDetection() {
        assertTrue(Arrays.stream(CompatContainerScreen.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == EditBox.class));
        assertTrue(Arrays.stream(CompatScreen.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == EditBox.class));
    }
}
