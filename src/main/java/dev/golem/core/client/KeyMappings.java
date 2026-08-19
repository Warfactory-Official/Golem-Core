package dev.golem.core.client;

import net.minecraft.client.KeyMapping;
//? if >=26.1 {
import java.util.HashMap;
import java.util.Map;
//?}

/**
 * Registers a mod's key mappings and hands back the vanilla object to poll.
 *
 * <p>Two things differ across the matrix. The <b>category</b> became a type: through 1.21.1 a mapping
 * takes a bare translation key {@code String}, while 26.1 takes a {@code KeyMapping.Category} record
 * that must itself be registered exactly once (its registrar throws on a duplicate). And the
 * <b>registration moment</b> differs by loader — fabric-api takes the mapping immediately through a
 * helper whose name changed at 26.1 ({@code KeyBindingHelper} to {@code KeyMappingHelper}), whereas
 * NeoForge and Forge only accept mappings inside a mod-bus event, so registration has to be deferred.
 *
 * <p>The category is named by path and built into {@code key.category.<namespace>.<path>} on every
 * version, which is exactly the key 26.1 derives from the category id — so one lang entry covers the
 * whole matrix.
 *
 * <p>What comes back is the vanilla {@link KeyMapping}: {@code isDown()} for held state and
 * {@code consumeClick()} for buffered presses both mean the same thing everywhere, so there is nothing
 * left to abstract.
 *
 * <p>Client-only: never call this from common initialization.
 */
public final class KeyMappings {

    private KeyMappings() {}

    //? if >=26.1 {
    /**
     * Categories already built, by id. 26.1's registrar throws outright on a second registration of the
     * same id, so several mappings sharing one category — the normal case — must share one instance.
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
        // Deferred: this loader only accepts mappings inside its own mod-bus event. Constructing the
        // mapping eagerly above is fine — that is what vanilla's own field initializers do. The category
        // goes over the same event, or the controls screen has nowhere to file the mapping.
        //
        // The category is registered by the FIRST mapping in it and no other: this loader's
        // registerCategory throws outright on a repeat ("KeyMapping.Category 'ns:path' is already
        // registered"), and every mapping doing it meant any category holding two mappings crashed the
        // client during Options construction — before the window opens, and therefore before anything
        // that looks like a mod error.
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

    /**
     * A mapping that can report its own held state reliably, even when it shares a key with a vanilla bind.
     *
     * <p>From 1.21 vanilla keeps {@code Map<Key, List<KeyMapping>>} and pushes state to every mapping on a
     * key, so two binds may share one button happily. <b>1.20.1 keeps {@code Map<Key, KeyMapping>}</b> —
     * one mapping per key, last registration wins — so a mod bind sharing a button with Pick Block or Use
     * never receives any state at all and reads as permanently unpressed. That is not a conflict the user
     * can see in the controls screen; the bind simply does nothing until it is moved to a free key.
     *
     * <p>On those versions {@link #isHeld()} therefore polls the device for this mapping's own bound key
     * instead of trusting the shared dispatch. Reading {@code key} needs no reflection or access widener:
     * it is {@code protected} on {@link KeyMapping}, and Golem constructs these itself.
     */
    public static final class Mapping extends KeyMapping {

        /** The button this mapping was declared with. Golem builds these, so it need not ask vanilla. */
        private final com.mojang.blaze3d.platform.InputConstants.Type declaredType;
        private final int declaredValue;

        //? if >=26.1 {
        Mapping(String name, com.mojang.blaze3d.platform.InputConstants.Type type, int value,
                KeyMapping.Category category) {
            super(name, type, value, category);
            this.declaredType = type;
            this.declaredValue = value;
        }
        //?} else {
        /*Mapping(String name, com.mojang.blaze3d.platform.InputConstants.Type type, int value,
                String category) {
            super(name, type, value, category);
            this.declaredType = type;
            this.declaredValue = value;
        }
        *///?}

        /**
         * Whether this mapping's key is held right now.
         *
         * <p>From 1.21 this is simply {@code isDown()}: vanilla keeps {@code Map<Key, List<KeyMapping>>}
         * and pushes state to every mapping sharing a key, so two binds may share a button happily.
         *
         * <p><b>1.20.1 keeps {@code Map<Key, KeyMapping>}</b> — one mapping per key, last registration
         * wins — so a mod bind sharing a mouse button with Use or Pick Block never receives state at all
         * and reads as permanently unpressed. There is no conflict shown in the controls screen; the bind
         * simply does nothing until the user moves it to a free key. There, a mapping declared on a mouse
         * button also polls that button directly.
         *
         * <p>Known limitation of that fallback: it polls the button this mapping was <em>declared</em>
         * with, because vanilla exposes no public accessor for the current one below 26.1 (the field is
         * private, and adding an access widener for it is not worth it). So on 1.20.1, rebinding a
         * mouse-declared mapping to something else leaves the original button working as well. Rebinding
         * to a keyboard key is otherwise the documented workaround for this whole problem, and that case
         * behaves correctly.
         */
        public boolean isHeld() {
            if (isDown()) return true;
            //? if <1.21 {
            /*if (declaredType == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
                long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
                return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, declaredValue)
                        == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            }
            *///?}
            return false;
        }
    }

    //? if >=26.1 {
    private static KeyMapping.Category category(String namespace, String path) {
        return CATEGORIES.computeIfAbsent(namespace + ":" + path,
                key -> KeyMapping.Category.register(dev.golem.core.Ids.of(namespace, path)));
    }
    //?}
}
