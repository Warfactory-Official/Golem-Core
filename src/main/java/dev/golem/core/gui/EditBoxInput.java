package dev.golem.core.gui;

/** Keyboard ownership policy for a focused text widget. */
final class EditBoxInput {

    private EditBoxInput() {}

    static boolean consumesKey(int key) {
        return key != 256 && key != 258;
    }
}
