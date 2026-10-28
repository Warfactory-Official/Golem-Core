package dev.golem.core.world;

/**
 * The loot function {@code golemcore:set_count}: 1.20.1's {@code minecraft:set_count} caps the count at the item's
 * stack size, so three of an unstackable item roll as one. Later versions do not cap it and split the stack when a
 * container is filled. The legacy build step rewrites {@code minecraft:set_count} to this on 1.20.1 only.
 */
public final class LootCounts {

    private LootCounts() {}

    public static void init() {
        //? if <1.20.5 {
        /*dev.golem.core.registry.DeferredRegister.create(() -> net.minecraft.core.registries.Registries.LOOT_FUNCTION_TYPE,
                dev.golem.core.GolemCore.MOD_ID).register("set_count", () -> SetCount.TYPE);
        *///?}
    }

    //? if <1.20.5 {
    /*private static final class SetCount extends net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction {

        static final net.minecraft.world.level.storage.loot.functions.LootItemFunctionType TYPE =
                new net.minecraft.world.level.storage.loot.functions.LootItemFunctionType(new Json());

        private final net.minecraft.world.level.storage.loot.providers.number.NumberProvider value;
        private final boolean add;

        SetCount(net.minecraft.world.level.storage.loot.predicates.LootItemCondition[] conditions,
                 net.minecraft.world.level.storage.loot.providers.number.NumberProvider value, boolean add) {
            super(conditions);
            this.value = value;
            this.add = add;
        }

        @Override
        public net.minecraft.world.level.storage.loot.functions.LootItemFunctionType getType() {
            return TYPE;
        }

        @Override
        public java.util.Set<net.minecraft.world.level.storage.loot.parameters.LootContextParam<?>> getReferencedContextParams() {
            return value.getReferencedContextParams();
        }

        @Override
        protected net.minecraft.world.item.ItemStack run(net.minecraft.world.item.ItemStack stack,
                                                         net.minecraft.world.level.storage.loot.LootContext context) {
            stack.setCount((add ? stack.getCount() : 0) + value.getInt(context));
            return stack;
        }
    }

    private static final class Json
            extends net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction.Serializer<SetCount> {
        @Override
        public void serialize(com.google.gson.JsonObject json, SetCount function,
                              com.google.gson.JsonSerializationContext context) {
            super.serialize(json, function, context);
            json.add("count", context.serialize(function.value));
            json.addProperty("add", function.add);
        }

        @Override
        public SetCount deserialize(com.google.gson.JsonObject json, com.google.gson.JsonDeserializationContext context,
                                    net.minecraft.world.level.storage.loot.predicates.LootItemCondition[] conditions) {
            return new SetCount(conditions, net.minecraft.util.GsonHelper.getAsObject(json, "count", context,
                    net.minecraft.world.level.storage.loot.providers.number.NumberProvider.class),
                    net.minecraft.util.GsonHelper.getAsBoolean(json, "add", false));
        }
    }
    *///?}
}
