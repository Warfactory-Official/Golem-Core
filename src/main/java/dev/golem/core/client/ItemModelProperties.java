package dev.golem.core.client;

import dev.golem.core.Ids;
import net.minecraft.world.item.ItemStack;

/**
 * Registers a string-valued item-model property on both item-model systems.
 *
 * <p>Modern item definitions select strings through a typed property. Older clients select the same
 * integer through their legacy float predicate, which is sufficient for integer variant cases.
 */
public final class ItemModelProperties {
    private ItemModelProperties() {}

    @FunctionalInterface
    public interface Value {
        int get(ItemStack stack);
    }

    public static void register(String namespace, String name, Value value) {
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
        /*registerLegacy(namespace, name, value);
        *///?}
    }

    //? if <26.1 {
    /*private static void registerLegacy(String namespace, String name, Value value) {
        //? if fabric {
        net.minecraft.resources.ResourceLocation id = Ids.of(namespace, name);
        registerLegacyItems(namespace, id, value);
        //?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(
                        () -> registerLegacyItems(namespace, Ids.of(namespace, name), value)));
        //?} elif forge {
        dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(
                        () -> registerLegacyItems(namespace, Ids.of(namespace, name), value)));
        //?}
    }

    private static void registerLegacyItems(String namespace, net.minecraft.resources.ResourceLocation id, Value value) {
        java.util.List<net.minecraft.world.item.Item> items = net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(namespace))
                .map(java.util.Map.Entry::getValue)
                .toList();
        items.forEach(item -> net.minecraft.client.renderer.item.ItemProperties.register(item, id, new Unclamped(value)));
        dev.golem.core.GolemCore.LOG.info("Legacy item model property {} installed for {} item(s)", id, items.size());
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
