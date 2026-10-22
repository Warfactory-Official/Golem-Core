package dev.golem.core.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.golem.core.GolemCore;
import dev.golem.core.registry.DeferredRegister;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * The loot condition {@code golemcore:requires}: holds while every literal of its {@code requires} list holds, the
 * same recipe flags as {@link RecipeTables#flag} ({@code name} or {@code !name}). Read at each drop, so a config
 * change applies at once. Dejson states it through {@code Loot.either}.
 */
public final class LootRequires implements LootItemCondition {

    public static final MapCodec<LootRequires> CODEC = Codec.STRING.listOf().fieldOf("requires")
            .xmap(LootRequires::new, condition -> condition.requires);

    //? if >=1.20.5 && <26.1 {
    /*private static final net.minecraft.world.level.storage.loot.predicates.LootItemConditionType TYPE =
            new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(CODEC);
    *///?} elif <1.20.5 {
    /*private static final net.minecraft.world.level.storage.loot.predicates.LootItemConditionType TYPE =
            new net.minecraft.world.level.storage.loot.predicates.LootItemConditionType(new Json());
    *///?}

    private static boolean registered;

    private final List<String> requires;

    private LootRequires(List<String> requires) {
        this.requires = List.copyOf(requires);
    }

    /** Registers the condition type once; {@link RecipeTables#flag} calls it, as any flag can gate loot. */
    static synchronized void register() {
        if (registered) return;
        registered = true;
        //? if >=26.1 {
        DeferredRegister.create(() -> Registries.LOOT_CONDITION_TYPE, GolemCore.MOD_ID).register("requires", () -> CODEC);
        //?} else {
        /*DeferredRegister.create(() -> Registries.LOOT_CONDITION_TYPE, GolemCore.MOD_ID).register("requires", () -> TYPE);
        *///?}
    }

    @Override
    public boolean test(LootContext context) {
        return RecipeTables.holds(requires, "loot condition " + requires);
    }

    //? if >=26.1 {
    @Override
    public MapCodec<? extends LootItemCondition> codec() {
        return CODEC;
    }
    //?} else {
    /*@Override
    public net.minecraft.world.level.storage.loot.predicates.LootItemConditionType getType() {
        return TYPE;
    }
    *///?}

    //? if <1.20.5 {
    /*private static final class Json implements net.minecraft.world.level.storage.loot.Serializer<LootRequires> {
        @Override
        public void serialize(com.google.gson.JsonObject json, LootRequires condition,
                              com.google.gson.JsonSerializationContext context) {
            com.google.gson.JsonArray list = new com.google.gson.JsonArray();
            condition.requires.forEach(list::add);
            json.add("requires", list);
        }

        @Override
        public LootRequires deserialize(com.google.gson.JsonObject json, com.google.gson.JsonDeserializationContext context) {
            List<String> list = new java.util.ArrayList<>();
            json.getAsJsonArray("requires").forEach(literal -> list.add(literal.getAsString()));
            return new LootRequires(list);
        }
    }
    *///?}
}
