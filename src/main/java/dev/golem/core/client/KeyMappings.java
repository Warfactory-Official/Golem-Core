package dev.golem.core.client;

import dev.golem.core.harness.HarnessHooks;
import net.minecraft.client.KeyMapping;
//? if >=26.1 {
import java.util.HashMap;
import java.util.Map;
//?}

/**
 * Registers a mod's key mappings and hands back the vanilla object to poll.
 *
 * <p>Two things differ across the matrix. Through 1.21.1 a mapping takes a bare translation key
 * {@code String} as its category; 26.1 takes a {@code KeyMapping.Category} record that must be registered
 * exactly once (its registrar throws on a duplicate). And fabric-api takes the mapping immediately (through a
 * helper renamed at 26.1, {@code KeyBindingHelper} to {@code KeyMappingHelper}), whereas NeoForge and Forge only
 * accept mappings inside a mod-bus event, so registration is deferred there.
 *
 * <p>The category is named by path and built into {@code key.category.<namespace>.<path>} on every
 * version, which is the key 26.1 derives from the category id, so one lang entry covers the matrix.
 *
 * <p>What comes back is the vanilla {@link KeyMapping}; {@code isDown()} and {@code consumeClick()} mean the
 * same thing everywhere.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class KeyMappings {

    private KeyMappings() {}

    //? if >=26.1 {
    /**
     * Categories already built, by id. 26.1's registrar throws outright on a second registration of the
     * same id, so several mappings sharing one category, the normal case, must share one instance.
     */
    private static final Map<String, KeyMapping.Category> CATEGORIES = new HashMap<>();
    //?}

    /**
     * Declares a key mapping bound to {@code keysym} (a GLFW key code) by default.
     *
     * @param namespace    the owning mod id, used for the category id and its lang key
     * @param translationKey the mapping's own name, e.g. {@code "key.hbm.reload"}
     * @param keysym       the default GLFW key code, or {@code InputConstants.UNKNOWN}'s value for unbound
     * @param categoryPath the category's path within {@code namespace}, e.g. {@code "weapons"}
     * @return the registered mapping, to poll with {@code isDown()}/{@code consumeClick()}
     */
    public static Mapping register(String namespace, String translationKey, int keysym,
            String categoryPath) {
        return register(namespace, translationKey, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                keysym, categoryPath);
    }

    /**
     * Declares a key mapping bound to a mouse button ({@code 0} left, {@code 1} right, {@code 2} middle)
     * or a keyboard key, depending on {@code type}.
     */
    public static Mapping register(String namespace, String translationKey,
            com.mojang.blaze3d.platform.InputConstants.Type type, int value, String categoryPath) {
        //? if >=26.1 {
        String categoryId = namespace + ":" + categoryPath;
        boolean firstOfCategory = !CATEGORIES.containsKey(categoryId);
        KeyMapping.Category category = category(namespace, categoryPath);
        Mapping mapping = new Mapping(translationKey, type, value, category);
        //?} else {
        /*Mapping mapping = new Mapping(translationKey, type, value,
                "key.category." + namespace + "." + categoryPath);
        *///?}

        //? if fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(mapping);
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
        *///?} elif neoforge && >=26.1 {
        // Deferred: this loader only accepts mappings inside its own mod-bus event. The category goes over
        // the same event, or the controls screen has nowhere to file the mapping.
        // Only the first mapping of a category registers it: registerCategory throws on a repeat, which
        // crashes the client during Options construction, before anything that looks like a mod error.
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> {
                    if (firstOfCategory) event.registerCategory(category);
                    event.register(mapping);
                });
        //?} elif neoforge {
        /*// Same deferral, but this era's event has no registerCategory — categories are bare strings.
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) -> event.register(mapping));
        *///?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterKeyMappingsEvent event) -> event.register(mapping));
        *///?}
        return mapping;
    }

    /** Whether a mapping's current binding is the given mouse button. */
    public static boolean matchesMouse(KeyMapping mapping, int button) {
        //? if >=26.1 {
        return mapping.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0, 0,
                new net.minecraft.client.input.MouseButtonInfo(button, 0)));
        //?} else {
        /*return mapping.matchesMouse(button);
        *///?}
    }

    /**
     * Whether the keyboard key {@code glfwKey} is physically held, for code that names a key rather than a
     * mapping (1.7's {@code Keyboard.isKeyDown}). Only the window handle's spelling differs by version.
     */
    public static boolean isKeyDown(int glfwKey) {
        //? if >=26.1 {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow(), glfwKey)
                || HarnessHooks.keyHeld(glfwKey);
        //?} else {
        /*return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), glfwKey)
                || HarnessHooks.keyHeld(glfwKey);
        *///?}
    }

    /** Physical held state for a mapping's current binding, independent of vanilla's dispatch map. */
    public static boolean isHeld(KeyMapping mapping) {
        com.mojang.blaze3d.platform.InputConstants.Key key =
                com.mojang.blaze3d.platform.InputConstants.getKey(mapping.saveString());
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return MouseInput.isDown(key.getValue());
        }
        if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            if (HarnessHooks.keyHeld(key.getValue())) return true;
            //? if >=26.1 {
            return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    net.minecraft.client.Minecraft.getInstance().getWindow(), key.getValue());
            //?} else {
            /*return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                    net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), key.getValue());
            *///?}
        }
        return mapping.isDown();
    }

    /**
     * A mapping that reports its own held state even when it shares a key with a vanilla bind.
     *
     * <p>26.1 vanilla keeps {@code Map<Key, List<KeyMapping>>} and pushes state to every mapping on a key.
     * 1.20.1 and 1.21.1 keep {@code Map<Key, KeyMapping>}, one mapping per key, so on Fabric (Forge/NeoForge
     * patch in a multi-valued lookup) a mod bind sharing a key with Sneak, Pick Block or Use never receives
     * state and reads as permanently unpressed. The controls screen shows no conflict.
     *
     * <p>{@link #isHeld()} therefore also reads the mapping that won the shared key and, below 26.1, polls the
     * device for this mapping's own bound key.
     */
    public static final class Mapping extends KeyMapping {

        //? if >=26.1 {
        Mapping(String name, com.mojang.blaze3d.platform.InputConstants.Type type, int value,
                KeyMapping.Category category) {
            super(name, type, value, category);
        }
        //?} else {
        /*Mapping(String name, com.mojang.blaze3d.platform.InputConstants.Type type, int value,
                String category) {
            super(name, type, value, category);
        }
        *///?}

        /**
         * Whether this mapping's key is held right now: its own state, any other mapping bound to the same
         * key, and below 26.1 a direct device poll of the bound key or mouse button (resolved from
         * {@link #saveString()}).
         */
        public boolean isHeld() {
            if (isDown()) return true;
            // Through 1.21.1 the single-valued dispatch map hands a shared key's state to whichever mapping
            // won it (on Fabric, e.g. Sneak over a mod bind on left Shift). Reading that mapping also covers
            // input injected through KeyboardHandler or MouseHandler, which the device poll cannot see.
            // Forge/NeoForge dispatch to every mapping on a key, so there the loop finds nothing new.
            for (KeyMapping other : net.minecraft.client.Minecraft.getInstance().options.keyMappings) {
                if (other != this && !other.isUnbound() && other.same(this) && other.isDown()) return true;
            }
            //? if <26.1 {
            /*return KeyMappings.isHeld(this);
            *///?}
            //? if >=26.1 {
            return false;
            //?}
        }
    }

    //? if >=26.1 {
    private static KeyMapping.Category category(String namespace, String path) {
        return CATEGORIES.computeIfAbsent(namespace + ":" + path,
                key -> KeyMapping.Category.register(dev.golem.core.Ids.of(namespace, path)));
    }
    //?}
}
