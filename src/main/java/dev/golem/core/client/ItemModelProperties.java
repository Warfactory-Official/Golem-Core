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
        /*net.minecraft.resources.ResourceLocation id = Ids.of(namespace, name);
        net.minecraft.core.registries.BuiltInRegistries.ITEM.entrySet().stream()
                .filter(entry -> entry.getKey().location().getNamespace().equals(namespace))
                .forEach(entry -> net.minecraft.client.renderer.item.ItemProperties.register(
                        entry.getValue(), id, (stack, level, entity, seed) -> value.get(stack)));
        *///?}
    }

    //? if >=26.1 {
    private record VariantProperty(Value value)
            implements net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty<String> {
        private static final com.mojang.serialization.Codec<String> VALUE_CODEC = com.mojang.serialization.Codec.STRING;
        private static final net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<VariantProperty, String> TYPE =
                net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type.create(
                        com.mojang.serialization.MapCodec.unit(new VariantProperty(stack -> 0)), VALUE_CODEC);

        @Override
        public String get(ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                          net.minecraft.world.entity.LivingEntity owner, int seed,
                          net.minecraft.world.item.ItemDisplayContext displayContext) {
            return Integer.toString(value.get(stack));
        }

        @Override
        public net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<VariantProperty, String> type() {
            return TYPE;
        }

        @Override
        public com.mojang.serialization.Codec<String> valueCodec() {
            return VALUE_CODEC;
        }
    }
    //?}
}
