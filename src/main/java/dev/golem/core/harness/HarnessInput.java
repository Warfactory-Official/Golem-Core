package dev.golem.core.harness;

import com.mojang.blaze3d.platform.InputConstants;
import dev.golem.core.harness.mixin.HarnessKeyboardHandlerAccessor;
import dev.golem.core.harness.mixin.HarnessMouseHandlerAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Synthetic input through the vanilla handlers, so every key binding, screen and slot sees exactly
 * what a physical device would produce. Nothing here touches {@code KeyMapping.setDown} directly.
 */
final class HarnessInput {

    /** A key or mouse button resolved from a name. */
    record Binding(boolean mouse, int code) {}

    private static final Map<String, Integer> NAMED = new HashMap<>();
    private static final Map<String, Integer> MOUSE_NAMED = new HashMap<>();
    // Digit and F keys are not consecutive codes on every node (SDL numbers 0 after 9).
    private static final int[] DIGITS = {InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3,
            InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8,
            InputConstants.KEY_9};
    private static final int[] F_KEYS = {InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3, InputConstants.KEY_F4,
            InputConstants.KEY_F5, InputConstants.KEY_F6, InputConstants.KEY_F7, InputConstants.KEY_F8, InputConstants.KEY_F9,
            InputConstants.KEY_F10, InputConstants.KEY_F11, InputConstants.KEY_F12};
    private static final Set<Binding> held = new LinkedHashSet<>();
    private static final List<long[]> scheduledReleases = new ArrayList<>(); // {tick, mouse(0/1), code}

    static {
        NAMED.put("space", InputConstants.KEY_SPACE);
        NAMED.put("escape", InputConstants.KEY_ESCAPE);
        NAMED.put("esc", InputConstants.KEY_ESCAPE);
        NAMED.put("enter", InputConstants.KEY_RETURN);
        NAMED.put("return", InputConstants.KEY_RETURN);
        NAMED.put("tab", InputConstants.KEY_TAB);
        NAMED.put("backspace", InputConstants.KEY_BACKSPACE);
        NAMED.put("delete", InputConstants.KEY_DELETE);
        NAMED.put("insert", InputConstants.KEY_INSERT);
        NAMED.put("home", InputConstants.KEY_HOME);
        NAMED.put("end", InputConstants.KEY_END);
        NAMED.put("pageup", InputConstants.KEY_PAGEUP);
        NAMED.put("pagedown", InputConstants.KEY_PAGEDOWN);
        NAMED.put("left", InputConstants.KEY_LEFT);
        NAMED.put("right", InputConstants.KEY_RIGHT);
        NAMED.put("up", InputConstants.KEY_UP);
        NAMED.put("down", InputConstants.KEY_DOWN);
        NAMED.put("lshift", InputConstants.KEY_LSHIFT);
        NAMED.put("shift", InputConstants.KEY_LSHIFT);
        NAMED.put("rshift", InputConstants.KEY_RSHIFT);
        NAMED.put("lctrl", InputConstants.KEY_LCONTROL);
        NAMED.put("ctrl", InputConstants.KEY_LCONTROL);
        NAMED.put("rctrl", InputConstants.KEY_RCONTROL);
        NAMED.put("lalt", InputConstants.KEY_LALT);
        NAMED.put("alt", InputConstants.KEY_LALT);
        NAMED.put("ralt", InputConstants.KEY_RALT);
        NAMED.put("capslock", InputConstants.KEY_CAPSLOCK);
        NAMED.put("minus", InputConstants.KEY_MINUS);
        NAMED.put("equal", InputConstants.KEY_EQUALS);
        NAMED.put("comma", InputConstants.KEY_COMMA);
        NAMED.put("period", InputConstants.KEY_PERIOD);
        NAMED.put("slash", InputConstants.KEY_SLASH);
        NAMED.put("semicolon", InputConstants.KEY_SEMICOLON);
        NAMED.put("apostrophe", InputConstants.KEY_APOSTROPHE);
        NAMED.put("grave", InputConstants.KEY_GRAVE);
        NAMED.put("lbracket", InputConstants.KEY_LBRACKET);
        NAMED.put("rbracket", InputConstants.KEY_RBRACKET);
        NAMED.put("backslash", InputConstants.KEY_BACKSLASH);
        for (int i = 0; i < 12; i++) NAMED.put("f" + (i + 1), F_KEYS[i]);
        for (int i = 0; i < 10; i++) NAMED.put(String.valueOf(i), DIGITS[i]);
        MOUSE_NAMED.put("mouse.left", InputConstants.MOUSE_BUTTON_LEFT);
        MOUSE_NAMED.put("mouse.right", InputConstants.MOUSE_BUTTON_RIGHT);
        MOUSE_NAMED.put("mouse.middle", InputConstants.MOUSE_BUTTON_MIDDLE);
    }

    private HarnessInput() {}

    /**
     * Resolves {@code key.use}, {@code key.hotbar.3}, {@code w}, {@code space}, {@code f3},
     * {@code mouse.left}, {@code mouse.right}, {@code glfw:<code>} or a bare number (the node's key code).
     */
    static Binding resolve(Minecraft minecraft, String name) {
        String n = name.trim();
        String lower = n.toLowerCase(Locale.ROOT);
        if (lower.startsWith("key.")) {
            for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (mapping.getName().equals(n)) {
                    // saveString() is the bound key on every loader; Forge-only getKey() is not.
                    InputConstants.Key key = InputConstants.getKey(mapping.saveString());
                    return new Binding(key.getType() == InputConstants.Type.MOUSE, key.getValue());
                }
            }
            throw new IllegalArgumentException("unknown key mapping " + n);
        }
        if (lower.startsWith("glfw:")) return new Binding(false, Integer.parseInt(lower.substring(5)));
        if (lower.startsWith("mouse:")) return new Binding(true, Integer.parseInt(lower.substring(6)));
        Integer mouse = MOUSE_NAMED.get(lower);
        if (mouse != null) return new Binding(true, mouse);
        Integer named = NAMED.get(lower);
        if (named != null) return new Binding(false, named);
        if (lower.length() == 1 && lower.charAt(0) >= 'a' && lower.charAt(0) <= 'z') {
            return new Binding(false, InputConstants.KEY_A + (lower.charAt(0) - 'a'));
        }
        try {
            return new Binding(false, Integer.parseInt(lower));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unknown key name " + n);
        }
    }

    /** The node's code for a scenario mouse button (0 left, 1 right, 2 middle); 26.3's SDL counts from 1. */
    static int mouseButton(int button) {
        return switch (button) {
            case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
            case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
            case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
            default -> button + InputConstants.MOUSE_BUTTON_LEFT;
        };
    }

    /** Whether the harness is holding keyboard key {@code key} down. */
    static boolean keyHeld(int key) {
        return held.contains(new Binding(false, key));
    }

    /** Whether the harness is holding mouse button {@code button} down. */
    static boolean mouseHeld(int button) {
        return held.contains(new Binding(true, button));
    }

    static void press(Minecraft minecraft, Binding binding, boolean down, int mods) {
        if (binding.mouse()) mouseButton(minecraft, binding.code(), down, mods);
        else key(minecraft, binding.code(), down, mods);
        if (down) held.add(binding);
        else held.remove(binding);
    }

    /** Press now, release after {@code holdTicks} ticks (1 = release next tick). */
    static void tap(Minecraft minecraft, Binding binding, int holdTicks, int mods) {
        press(minecraft, binding, true, mods);
        if (holdTicks <= 0) {
            press(minecraft, binding, false, mods);
        } else {
            scheduledReleases.add(new long[] {Harness.tick() + holdTicks, binding.mouse() ? 1 : 0, binding.code(), mods});
        }
    }

    static boolean releasesPending() {
        return !scheduledReleases.isEmpty();
    }

    static void releaseAll(Minecraft minecraft) {
        for (Binding binding : new ArrayList<>(held)) press(minecraft, binding, false, 0);
        scheduledReleases.clear();
    }

    static void tick(Minecraft minecraft) {
        if (scheduledReleases.isEmpty()) return;
        long now = Harness.tick();
        for (Iterator<long[]> it = scheduledReleases.iterator(); it.hasNext(); ) {
            long[] entry = it.next();
            if (now >= entry[0]) {
                it.remove();
                press(minecraft, new Binding(entry[1] == 1, (int) entry[2]), false, (int) entry[3]);
            }
        }
    }

    static void key(Minecraft minecraft, int key, boolean down, int mods) {
        long window = HarnessWindow.handle(minecraft);
        int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
        //? if >=26.3 {
        /*int scancode = org.lwjgl.sdl.SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, false);
        *///?} else {
        int scancode = org.lwjgl.glfw.GLFW.glfwGetKeyScancode(key);
        //?}
        //? if >=1.21.11 {
        ((HarnessKeyboardHandlerAccessor) minecraft.keyboardHandler).golem$keyPress(window, action,
                new net.minecraft.client.input.KeyEvent(key, scancode, mods));
        //?} else {
        /*minecraft.keyboardHandler.keyPress(window, key, scancode, action, mods);
        *///?}
    }

    static void charTyped(Minecraft minecraft, int codepoint, int mods) {
        long window = HarnessWindow.handle(minecraft);
        //? if >=26.1 {
        ((HarnessKeyboardHandlerAccessor) minecraft.keyboardHandler).golem$charTyped(window,
                new net.minecraft.client.input.CharacterEvent(codepoint));
        //?} elif >=1.21.11 {
        /*((HarnessKeyboardHandlerAccessor) minecraft.keyboardHandler).golem$charTyped(window,
                new net.minecraft.client.input.CharacterEvent(codepoint, mods));
        *///?} else {
        /*((HarnessKeyboardHandlerAccessor) minecraft.keyboardHandler).golem$charTyped(window, codepoint, mods);
        *///?}
    }

    static void mouseButton(Minecraft minecraft, int button, boolean down, int mods) {
        long window = HarnessWindow.handle(minecraft);
        int action = down ? InputConstants.PRESS : InputConstants.RELEASE;
        //? if >=1.21.11 {
        ((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onButton(window,
                new net.minecraft.client.input.MouseButtonInfo(button, mods), action);
        //?} else {
        /*((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onPress(window, button, action, mods);
        *///?}
    }

    /** Moves the cursor to GUI-scaled coordinates (the space screens and slots are laid out in). */
    static double[] mouseMove(Minecraft minecraft, double guiX, double guiY) {
        long window = HarnessWindow.handle(minecraft);
        double px = guiX * minecraft.getWindow().getScreenWidth() / (double) minecraft.getWindow().getGuiScaledWidth();
        double py = guiY * minecraft.getWindow().getScreenHeight() / (double) minecraft.getWindow().getGuiScaledHeight();
        //? if >=26.3 {
        /*((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onMove(window, px, py,
                px - minecraft.mouseHandler.xpos(), py - minecraft.mouseHandler.ypos());
        *///?} else {
        ((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onMove(window, px, py);
        //?}
        return new double[] {px, py, minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()};
    }

    static void scroll(Minecraft minecraft, double dx, double dy) {
        long window = HarnessWindow.handle(minecraft);
        ((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onScroll(window, dx, dy);
    }

    /** Current cursor position in GUI-scaled coordinates. */
    static double[] cursorGui(Minecraft minecraft) {
        double x = minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / (double) minecraft.getWindow().getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / (double) minecraft.getWindow().getScreenHeight();
        return new double[] {x, y};
    }
}
