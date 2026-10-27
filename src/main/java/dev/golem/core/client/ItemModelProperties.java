package dev.golem.core.client;

import dev.golem.core.GolemCore;
//? if neoforge {
import dev.golem.core.platform.NeoForgeEntrypoint;
//?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
//?}
import dev.golem.core.Ids;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.HashMap;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
//? if <26.1 {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
//? if >=26.1 {
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
//?}
import net.minecraft.world.item.ItemStack;

/**
 * Native string selections and legacy float predicates for item models.
 * Legacy string thresholds follow the original definition's case order; numeric predicates keep their values.
 */
public final class ItemModelProperties {
    private ItemModelProperties() {}

    @FunctionalInterface
    public interface Value {
        int get(ItemStack stack);
    }

    /** One property: its value function and the item namespaces that carry it below 26.1. */
    private static final class Property {
        ToDoubleFunction<ItemStack> value;
        Function<ItemStack, String> select;
        //? if <26.1 {
        /*Map<ResourceLocation, Selection> selections = Map.of();
        *///?}
        final Set<String> namespaces = new LinkedHashSet<>();
        final Set<String> installed = new HashSet<>();
    }

    private static final Map<String, Property> PROPERTIES = new LinkedHashMap<>();

    /** Registers {@code namespace:name} for the items of {@code namespace}. */
    public static void register(String namespace, String name, Value value) {
        register(namespace, name, value, new String[] {namespace});
    }

    /**
     * Registers {@code namespace:name} (the property id) for the items of {@code itemNamespaces}. On 26.1 the
     * namespaces change nothing (the property is global); a second registration of the same id is ignored there
     * with a warning, and below 26.1 it only adds its namespaces.
     */
    public static synchronized void register(String namespace, String name, Value value, String... itemNamespaces) {
        String key = namespace + ":" + name;
        Property property = PROPERTIES.computeIfAbsent(key, k -> new Property());
        Collections.addAll(property.namespaces, itemNamespaces);
        if (property.select != null) {
            GolemCore.LOG.warn("Item model property {} registered twice; the first value is kept", key);
            schedule(namespace, name);
            return;
        }
        property.value = value::get;
        property.select = stack -> Integer.toString(value.get(stack));
        //? if >=26.1 {
        registerNative(namespace, name, property.select);
        //?} else {
        /*schedule(namespace, name);
        *///?}
    }

    /** Selectors share the native definition's case keys and legacy predicate thresholds. */
    public static synchronized void registerSelect(String namespace, String name, Function<ItemStack, String> value) {
        var property = PROPERTIES.computeIfAbsent(namespace + ":" + name, key -> new Property());
        if (property.select != null) throw new IllegalArgumentException("Item select property already registered: " + namespace + ":" + name);
        property.select = value;
        property.namespaces.add(namespace);
        //? if >=26.1 {
        registerNative(namespace, name, value);
        //?} else {
        /*property.value = stack -> {
            var selection = property.selections.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (selection == null) return 0;
            var selected = value.apply(stack);
            var fallback = selection.numeric ? Float.NEGATIVE_INFINITY : 0F;
            return selected == null ? fallback : selection.cases.getOrDefault(selected, fallback);
        };
        schedule(namespace, name);
        *///?}
    }

    static boolean registeredSelect(String property) {
        var registered = PROPERTIES.get(property);
        return registered != null && registered.select != null;
    }

    static String selected(String property, ItemStack stack) {
        var registered = PROPERTIES.get(property);
        return registered == null || registered.select == null ? null : registered.select.apply(stack);
    }

    //? if >=26.1 {
    private static void registerNative(String namespace, String name, Function<ItemStack, String> value) {
        var property = new SelectProperty(value);
        //? if fabric {
        /*SelectItemModelProperties.ID_MAPPER.put(Ids.of(namespace, name), property.type());
        *///?} elif neoforge {
        NeoForgeEntrypoint.modBus().addListener(
                (RegisterSelectItemModelPropertyEvent event) ->
                        event.register(Ids.of(namespace, name), property.type()));
        //?}
    }
    //?}

    /**
     * Makes the property {@code namespace:name} (registered by its owner, before or after this call) apply to the
     * items of {@code itemNamespaces} as well. Needed below 26.1 only, where predicates are per item; a no-op on 26.1.
     */
    public static synchronized void includeItems(String namespace, String name, String... itemNamespaces) {
        Property property = PROPERTIES.computeIfAbsent(namespace + ":" + name, k -> new Property());
        java.util.Collections.addAll(property.namespaces, itemNamespaces);
        schedule(namespace, name);
    }

    /** Installs the legacy predicate on the namespaces not yet installed, at the loader's client-setup time. */
    private static void schedule(String namespace, String name) {
        //? if <26.1 {
        /*registerLegacy(namespace, name);
        *///?}
    }

    //? if <26.1 {
    /*private record Selection(Map<String, Float> cases, boolean numeric) {}

    static synchronized void definitions(Map<ResourceLocation, JsonObject> definitions) {
        var built = new HashMap<String, Map<ResourceLocation, Selection>>();
        for (var entry : definitions.entrySet()) {
            var model = entry.getValue().getAsJsonObject("model");
            if (model == null || !model.has("type") || !model.get("type").getAsString().equals("minecraft:select")) continue;
            var id = model.get("property").getAsString();
            var property = PROPERTIES.get(id);
            if (property == null || property.select == null) continue;
            var cases = model.getAsJsonArray("cases");
            var values = new float[cases.size()];
            var numeric = true;
            for (var i = 0; i < cases.size(); i++) {
                try {
                    values[i] = Float.parseFloat(cases.get(i).getAsJsonObject().get("when").getAsString());
                } catch (NumberFormatException notNumeric) {
                    numeric = false;
                }
            }
            var indices = new HashMap<String, Float>();
            for (var i = 0; i < cases.size(); i++) {
                indices.put(cases.get(i).getAsJsonObject().get("when").getAsString(), numeric ? values[i] : i + 1F);
            }
            built.computeIfAbsent(id, key -> new HashMap<>()).put(entry.getKey(), new Selection(Map.copyOf(indices), numeric));
            property.namespaces.add(entry.getKey().getNamespace());
        }
        for (var entry : PROPERTIES.entrySet()) {
            entry.getValue().selections = Map.copyOf(built.getOrDefault(entry.getKey(), Map.of()));
            var split = entry.getKey().indexOf(':');
            installLegacy(entry.getKey().substring(0, split), entry.getKey().substring(split + 1));
        }
    }

    private static void registerLegacy(String namespace, String name) {
        //? if fabric {
        installLegacy(namespace, name);
        //?} elif neoforge {
        NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(
                        () -> installLegacy(namespace, name)));
        //?} elif forge {
        dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(
                        () -> installLegacy(namespace, name)));
        //?}
    }

    private static synchronized void installLegacy(String namespace, String name) {
        Property property = PROPERTIES.get(namespace + ":" + name);
        // includeItems before the owner's register: the owner's call installs them
        if (property == null || property.value == null) return;
        net.minecraft.resources.ResourceLocation id = Ids.of(namespace, name);
        for (String itemNamespace : property.namespaces) {
            if (!property.installed.add(itemNamespace)) continue;
            java.util.List<net.minecraft.world.item.Item> items = net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(entry -> entry.getKey().location().getNamespace().equals(itemNamespace))
                    .map(java.util.Map.Entry::getValue)
                    .toList();
            Unclamped function = new Unclamped(property.value);
            items.forEach(item -> net.minecraft.client.renderer.item.ItemProperties.register(item, id, function));
            dev.golem.core.GolemCore.LOG.info("Legacy item model property {} installed for {} item(s) of {}", id, items.size(), itemNamespace);
        }
    }

    /^*
     * Vanilla's public {@code ItemProperties.register} takes a {@code ClampedItemPropertyFunction}, whose default
     * {@code call} clamps to 0..1. Forge and NeoForge add an unclamped overload a lambda binds to; Fabric has only
     * vanilla's, so every variant above 1 resolved to variant 1's override there (for example
     * satellite, weapon_mod_special, weapon_mod_caliber, part_barrel_heavy on 1.20.1-fabric). Overriding {@code call}
     * returns the raw integer on every loader, which is what the {@code >=} override thresholds need.
     ^/
    private record Unclamped(ToDoubleFunction<ItemStack> value)
            implements net.minecraft.client.renderer.item.ClampedItemPropertyFunction {
        @Override
        public float call(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                          net.minecraft.world.entity.LivingEntity entity, int seed) {
            return (float) value.applyAsDouble(stack);
        }

        @Override
        public float unclampedCall(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                                   net.minecraft.world.entity.LivingEntity entity, int seed) {
            return (float) value.applyAsDouble(stack);
        }
    }
    *///?}

    //? if >=26.1 {
    static final class SelectProperty implements SelectItemModelProperty<String> {
        private final Function<ItemStack, String> value;
        private final Type<SelectProperty, String> type;

        SelectProperty(Function<ItemStack, String> value) {
            this.value = value;
            type = Type.create(MapCodec.unit(this), Codec.STRING);
        }

        @Override
        public String get(ItemStack stack, ClientLevel level, LivingEntity owner, int seed, ItemDisplayContext displayContext) {
            return value.apply(stack);
        }

        @Override public Type<SelectProperty, String> type() { return type; }
        @Override public Codec<String> valueCodec() { return Codec.STRING; }
    }
    //?}
}
