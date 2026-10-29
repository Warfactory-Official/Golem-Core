package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import dev.golem.core.registry.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Loot functions a mod states as a codec and a stack transform, registered the same way on every version. The
 * function has no {@code conditions} of its own; gate it on the entry instead.
 */
public final class LootFunctions {

    private LootFunctions() {
    }

    public interface Transform {
        ItemStack apply(ItemStack stack, LootContext context);
    }

    /**
     * Registers {@code namespace:name}, read and written with {@code codec}. Call during mod construction.
     */
    public static <T extends Transform> void register(String namespace, String name, MapCodec<T> codec) {
        Kind<T> kind = new Kind<>(codec);
        //? if >=26.1 {
        DeferredRegister.create(() -> Registries.LOOT_FUNCTION_TYPE, namespace).register(name, () -> kind.codec);
        //?} else {
        /*DeferredRegister.create(() -> Registries.LOOT_FUNCTION_TYPE, namespace).register(name, () -> kind.type);
         *///?}
    }

    private static final class Kind<T extends Transform> {
        final MapCodec<Function<T>> codec;
        //? if >=1.20.5 && <26.1 {
        /*final net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<Function<T>> type;
         *///?} elif <1.20.5 {
        /*final net.minecraft.world.level.storage.loot.functions.LootItemFunctionType type;
         *///?}

        Kind(MapCodec<T> value) {
            codec = value.xmap(transform -> new Function<>(this, transform), function -> function.value);
            //? if >=1.20.5 && <26.1 {
            /*type = new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<>(codec);
             *///?} elif <1.20.5 {
            /*type = new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType(new LootJson<>(codec));
             *///?}
        }
    }

    private record Function<T extends Transform>(Kind<T> kind, T value) implements LootItemFunction {

        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            return value.apply(stack, context);
        }

        //? if >=26.1 {
        @Override
        public MapCodec<? extends LootItemFunction> codec() {
            return kind.codec;
        }
        //?} elif >=1.20.5 {
        /*@Override
        public net.minecraft.world.level.storage.loot.functions.LootItemFunctionType<? extends LootItemFunction> getType() {
            return kind.type;
        }
        *///?} else {
        /*@Override
        public net.minecraft.world.level.storage.loot.functions.LootItemFunctionType getType() {
            return kind.type;
        }
        *///?}
    }
}
