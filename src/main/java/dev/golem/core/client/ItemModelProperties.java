package dev.golem.core.client;

import dev.golem.core.Ids;
import net.minecraft.world.item.ItemStack;

/**
 * Registers a string-valued item-model property on both item-model systems.
 *
 * <p>Modern item definitions select strings through a typed property. Older clients select the same
 * integer through their legacy float predicate, which is sufficient for integer variant cases.
 *
 * <p>A modern select property is global: any item definition may select on it. A legacy predicate is
 * installed per item, so below 26.1 the property reaches only the items of the namespaces it is given:
 * the property's own namespace with {@link #register(String, String, Value)}, the listed ones with the
 * {@code itemNamespaces} overload, and more later with {@link #includeItems} (an addon whose items select
 * on another mod's property). Namespaces may be added before or after the property is registered.
 */
public final class ItemModelProperties {
    private ItemModelProperties() {}

    @FunctionalInterface
    public interface Value {
        int get(ItemStack stack);
    }

    /** One property: its value function and the item namespaces that carry it below 26.1. */
    private static final class Property {
        Value value;
        final java.util.Set<String> namespaces = new java.util.LinkedHashSet<>();
        final java.util.Set<String> installed = new java.util.HashSet<>();
    }

    private static final java.util.Map<String, Property> PROPERTIES = new java.util.LinkedHashMap<>();

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
        java.util.Collections.addAll(property.namespaces, itemNamespaces);
        if (property.value != null) {
            dev.golem.core.GolemCore.LOG.warn("Item model property {} registered twice; the first value is kept", key);
            schedule(namespace, name);
            return;
        }
        property.value = value;
        //? if >=26.1 {
        //? if fabric {
        /*net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties.ID_MAPPER.put(
                Ids.of(namespace, name), new VariantProperty(value).type());
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent event) ->
                        event.register(Ids.of(namespace, name), new VariantProperty(value).type()));
        //?}
        //?} else {
        /*schedule(namespace, name);
        *///?}
    }

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
    /*private static void registerLegacy(String namespace, String name) {
        //? if fabric {
        installLegacy(namespace, name);
        //?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
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
     * vanilla's, so every variant above 1 resolved to variant 1's override there (icon audit 2026-10-01:
     * satellite, weapon_mod_special, weapon_mod_caliber, part_barrel_heavy on 1.20.1-fabric). Overriding {@code call}
     * returns the raw integer on every loader, which is what the {@code >=} override thresholds need.
     ^/
    private record Unclamped(Value value)
            implements net.minecraft.client.renderer.item.ClampedItemPropertyFunction {
        @Override
        public float call(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                          net.minecraft.world.entity.LivingEntity entity, int seed) {
            return value.get(stack);
        }

        @Override
        public float unclampedCall(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                                   net.minecraft.world.entity.LivingEntity entity, int seed) {
            return value.get(stack);
        }
    }
    *///?}

    //? if >=26.1 {
    record VariantProperty(Value value)
            implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        private static final com.mojang.serialization.Codec<String> VALUE_CODEC = com.mojang.serialization.Codec.STRING;

        @Override
        public String get(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                          net.minecraft.world.entity.LivingEntity owner, int seed,
                          net.minecraft.world.item.ItemDisplayContext displayContext) {
            return Integer.toString(value.get(stack));
        }

        @Override
        public net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<VariantProperty, String> type() {
            return net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type.create(
                    com.mojang.serialization.MapCodec.unit(this), VALUE_CODEC);
        }

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return VALUE_CODEC;
        }
    }
    //?}
}
