package dev.golem.core.harness;

import com.mojang.blaze3d.platform.InputConstants;
import dev.golem.core.mixin.HarnessKeyboardHandlerAccessor;
import dev.golem.core.mixin.HarnessMouseHandlerAccessor;
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
import org.lwjgl.glfw.GLFW;

/**
 * Synthetic input through the vanilla handlers, so every key binding, screen and slot sees exactly
 * what a physical device would produce. Nothing here touches {@code KeyMapping.setDown} directly.
 */
final class HarnessInput {

    /** A key or mouse button resolved from a name. */
    record Binding(boolean mouse, int code) {}

    private static final Map<String, Integer> NAMED = new HashMap<>();
    private static final Set<Binding> held = new LinkedHashSet<>();
    private static final List<long[]> scheduledReleases = new ArrayList<>(); // {tick, mouse(0/1), code}

    static {
        NAMED.put("space", GLFW.GLFW_KEY_SPACE);
        NAMED.put("escape", GLFW.GLFW_KEY_ESCAPE);
        NAMED.put("esc", GLFW.GLFW_KEY_ESCAPE);
        NAMED.put("enter", GLFW.GLFW_KEY_ENTER);
        NAMED.put("return", GLFW.GLFW_KEY_ENTER);
        NAMED.put("tab", GLFW.GLFW_KEY_TAB);
        NAMED.put("backspace", GLFW.GLFW_KEY_BACKSPACE);
        NAMED.put("delete", GLFW.GLFW_KEY_DELETE);
        NAMED.put("insert", GLFW.GLFW_KEY_INSERT);
        NAMED.put("home", GLFW.GLFW_KEY_HOME);
        NAMED.put("end", GLFW.GLFW_KEY_END);
        NAMED.put("pageup", GLFW.GLFW_KEY_PAGE_UP);
        NAMED.put("pagedown", GLFW.GLFW_KEY_PAGE_DOWN);
        NAMED.put("left", GLFW.GLFW_KEY_LEFT);
        NAMED.put("right", GLFW.GLFW_KEY_RIGHT);
        NAMED.put("up", GLFW.GLFW_KEY_UP);
        NAMED.put("down", GLFW.GLFW_KEY_DOWN);
        NAMED.put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        NAMED.put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        NAMED.put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        NAMED.put("lctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        NAMED.put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        NAMED.put("rctrl", GLFW.GLFW_KEY_RIGHT_CONTROL);
        NAMED.put("lalt", GLFW.GLFW_KEY_LEFT_ALT);
        NAMED.put("alt", GLFW.GLFW_KEY_LEFT_ALT);
        NAMED.put("ralt", GLFW.GLFW_KEY_RIGHT_ALT);
        NAMED.put("capslock", GLFW.GLFW_KEY_CAPS_LOCK);
        NAMED.put("minus", GLFW.GLFW_KEY_MINUS);
        NAMED.put("equal", GLFW.GLFW_KEY_EQUAL);
        NAMED.put("comma", GLFW.GLFW_KEY_COMMA);
        NAMED.put("period", GLFW.GLFW_KEY_PERIOD);
        NAMED.put("slash", GLFW.GLFW_KEY_SLASH);
        NAMED.put("semicolon", GLFW.GLFW_KEY_SEMICOLON);
        NAMED.put("apostrophe", GLFW.GLFW_KEY_APOSTROPHE);
        NAMED.put("grave", GLFW.GLFW_KEY_GRAVE_ACCENT);
        NAMED.put("lbracket", GLFW.GLFW_KEY_LEFT_BRACKET);
        NAMED.put("rbracket", GLFW.GLFW_KEY_RIGHT_BRACKET);
        NAMED.put("backslash", GLFW.GLFW_KEY_BACKSLASH);
        for (int i = 1; i <= 12; i++) NAMED.put("f" + i, GLFW.GLFW_KEY_F1 + i - 1);
        NAMED.put("mouse.left", -1);
        NAMED.put("mouse.right", -2);
        NAMED.put("mouse.middle", -3);
    }

    private HarnessInput() {}

    /**
     * Resolves {@code key.use}, {@code key.hotbar.3}, {@code w}, {@code space}, {@code f3},
     * {@code mouse.left}, {@code mouse.right}, {@code glfw:<code>} or a bare number (GLFW key code).
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
        Integer named = NAMED.get(lower);
        if (named != null) {
            if (named < 0) return new Binding(true, -named - 1);
            return new Binding(false, named);
        }
        if (lower.length() == 1) {
            char c = lower.charAt(0);
            if (c >= 'a' && c <= 'z') return new Binding(false, GLFW.GLFW_KEY_A + (c - 'a'));
            if (c >= '0' && c <= '9') return new Binding(false, GLFW.GLFW_KEY_0 + (c - '0'));
        }
        try {
            return new Binding(false, Integer.parseInt(lower));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("unknown key name " + n);
        }
    }

    /** Whether the harness is holding keyboard key {@code key} down. */
    static boolean keyHeld(int key) {
        return held.contains(new Binding(false, key));
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
        int scancode = GLFW.glfwGetKeyScancode(key);
        int action = down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
        //? if >=26.1 {
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
        //?} else {
        /*((HarnessKeyboardHandlerAccessor) minecraft.keyboardHandler).golem$charTyped(window, codepoint, mods);
        *///?}
    }

    static void mouseButton(Minecraft minecraft, int button, boolean down, int mods) {
        long window = HarnessWindow.handle(minecraft);
        int action = down ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE;
        //? if >=26.1 {
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
        ((HarnessMouseHandlerAccessor) minecraft.mouseHandler).golem$onMove(window, px, py);
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
