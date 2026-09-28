package dev.golem.core.advancement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * A custom advancement criterion trigger, declared once and fired from gameplay code on every node.
 *
 * <p>A modded trigger is three moving parts that all changed between 1.20.1 and 1.21: the instance
 * (a {@code JsonObject} parsed by {@code createInstance} on 1.20.1, a {@code Codec}-backed
 * {@code SimpleInstance} since), its registration ({@code CriteriaTriggers.register} on 1.20.1, the
 * {@code TRIGGER_TYPES} registry since) and the predicate classes' own parsers (the {@code fromJson}
 * statics on 1.20.1, codecs since, in the {@code critereon} package until 26.1 renamed it
 * {@code criterion}). None of that is about what a mod's event means, so a consumer names its trigger
 * here and fires it; the per-version machinery stays inside.
 *
 * <p>Every trigger made here accepts the same JSON conditions, all optional, on every node:
 *
 * <pre>{@code
 * "criteria": { "x": { "trigger": "hbm:detonation", "conditions": {
 *     "player": { ... entity predicate ... },          // vanilla player predicate
 *     "kinds":  ["nuke", "rbmk"],                       // the event's kind is one of these
 *     "item":   { "items": ["hbm:sat_foeq"] },          // the event's stack matches this item predicate
 *     "value":  { "min": 200.0 }                        // the event's number lies in these bounds
 * } } }
 * }</pre>
 *
 * <p>Write item predicates with an {@code items} <i>list</i>: 1.20.1 only reads the list form, and every
 * later version accepts it too.
 */
public final class Trigger {

    private static final Map<String, Trigger> BY_ID = new ConcurrentHashMap<>();

    private final String id;
    private final Impl impl;

    private Trigger(String id, Impl impl) {
        this.id = id;
        this.impl = impl;
    }

    /**
     * Declares the trigger {@code namespace:path}. Call during mod construction, before the loader flushes
     * registries; the id is what advancement JSON names in its {@code trigger} field.
     */
    public static synchronized Trigger register(String namespace, String path) {
        String id = namespace + ":" + path;
        Trigger existing = BY_ID.get(id);
        if (existing != null) return existing;
        Impl impl = new Impl(dev.golem.core.Ids.of(namespace, path));
        // 1.20.1: CriteriaTriggers' table is a plain HashMap, and Forge constructs mods in parallel.
        //? if >=1.20.5 {
        TYPES.computeIfAbsent(namespace, ns -> dev.golem.core.registry.DeferredRegister.create(
                net.minecraft.core.registries.Registries.TRIGGER_TYPE, ns)).register(path, () -> impl);
        //?} else {
        /*synchronized (net.minecraft.advancements.CriteriaTriggers.class) {
            net.minecraft.advancements.CriteriaTriggers.register(impl);
        }
        *///?}
        Trigger trigger = new Trigger(id, impl);
        BY_ID.put(id, trigger);
        return trigger;
    }

    //? if >=1.20.5 {
    private static final Map<String, dev.golem.core.registry.DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>>> TYPES =
            new ConcurrentHashMap<>();
    //?}

    /** The trigger's id, e.g. {@code hbm:detonation}. */
    public String id() {
        return id;
    }

    /** Fires with no payload: only {@code player} conditions can reject it. */
    public void fire(ServerPlayer player) {
        fire(player, null, ItemStack.EMPTY, 0D);
    }

    /** Fires an event of one kind, matched by {@code kinds}. */
    public void fire(ServerPlayer player, String kind) {
        fire(player, kind, ItemStack.EMPTY, 0D);
    }

    /** Fires an event carrying a stack, matched by {@code item}. */
    public void fire(ServerPlayer player, ItemStack stack) {
        fire(player, null, stack, 0D);
    }

    /** Fires an event carrying a number, matched by {@code value}. */
    public void fire(ServerPlayer player, double value) {
        fire(player, null, ItemStack.EMPTY, value);
    }

    /**
     * Fires the event. Every advancement listening to this trigger whose conditions accept the event, and
     * whose {@code player} predicate accepts the player, gets the criterion.
     */
    public void fire(ServerPlayer player, String kind, ItemStack stack, double value) {
        impl.fire(player, kind, stack == null ? ItemStack.EMPTY : stack, value);
    }

    /** Whether conditions would accept an event; exposed so tests can check a predicate without a player. */
    public static boolean matches(List<String> kinds, String kind) {
        return kinds.isEmpty() || kind != null && kinds.contains(kind);
    }

    //? if >=26.1 {
    private static final class Impl extends net.minecraft.advancements.criterion.SimpleCriterionTrigger<Impl.Instance> {

        Impl(Object id) {}

        @Override
        public com.mojang.serialization.Codec<Instance> codec() {
            return Instance.CODEC;
        }

        void fire(ServerPlayer player, String kind, ItemStack stack, double amount) {
            trigger(player, instance -> instance.test(kind, stack, amount));
        }

        record Instance(java.util.Optional<net.minecraft.advancements.criterion.ContextAwarePredicate> player,
                        List<String> kinds,
                        java.util.Optional<net.minecraft.advancements.criterion.ItemPredicate> item,
                        java.util.Optional<net.minecraft.advancements.criterion.MinMaxBounds.Doubles> value)
                implements net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance {

            static final com.mojang.serialization.Codec<Instance> CODEC =
                    com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                            net.minecraft.advancements.criterion.EntityPredicate.ADVANCEMENT_CODEC
                                    .optionalFieldOf("player").forGetter(Instance::player),
                            com.mojang.serialization.Codec.STRING.listOf()
                                    .optionalFieldOf("kinds", List.of()).forGetter(Instance::kinds),
                            net.minecraft.advancements.criterion.ItemPredicate.CODEC
                                    .optionalFieldOf("item").forGetter(Instance::item),
                            net.minecraft.advancements.criterion.MinMaxBounds.Doubles.CODEC
                                    .optionalFieldOf("value").forGetter(Instance::value)
                    ).apply(i, Instance::new));

            boolean test(String kind, ItemStack stack, double amount) {
                if (!Trigger.matches(kinds, kind)) return false;
                if (item.isPresent() && !item.get().test(stack)) return false;
                return value.isEmpty() || value.get().matches(amount);
            }
        }
    }
    //?} elif >=1.20.5 {
    /*private static final class Impl extends net.minecraft.advancements.critereon.SimpleCriterionTrigger<Impl.Instance> {

        Impl(Object id) {}

        @Override
        public com.mojang.serialization.Codec<Instance> codec() {
            return Instance.CODEC;
        }

        void fire(ServerPlayer player, String kind, ItemStack stack, double amount) {
            trigger(player, instance -> instance.test(kind, stack, amount));
        }

        record Instance(java.util.Optional<net.minecraft.advancements.critereon.ContextAwarePredicate> player,
                        List<String> kinds,
                        java.util.Optional<net.minecraft.advancements.critereon.ItemPredicate> item,
                        java.util.Optional<net.minecraft.advancements.critereon.MinMaxBounds.Doubles> value)
                implements net.minecraft.advancements.critereon.SimpleCriterionTrigger.SimpleInstance {

            static final com.mojang.serialization.Codec<Instance> CODEC =
                    com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                            net.minecraft.advancements.critereon.EntityPredicate.ADVANCEMENT_CODEC
                                    .optionalFieldOf("player").forGetter(Instance::player),
                            com.mojang.serialization.Codec.STRING.listOf()
                                    .optionalFieldOf("kinds", List.of()).forGetter(Instance::kinds),
                            net.minecraft.advancements.critereon.ItemPredicate.CODEC
                                    .optionalFieldOf("item").forGetter(Instance::item),
                            net.minecraft.advancements.critereon.MinMaxBounds.Doubles.CODEC
                                    .optionalFieldOf("value").forGetter(Instance::value)
                    ).apply(i, Instance::new));

            boolean test(String kind, ItemStack stack, double amount) {
                if (!Trigger.matches(kinds, kind)) return false;
                if (item.isPresent() && !item.get().test(stack)) return false;
                return value.isEmpty() || value.get().matches(amount);
            }
        }
    }
    *///?} else {
    /*private static final class Impl
            extends net.minecraft.advancements.critereon.SimpleCriterionTrigger<Impl.Instance> {

        private final net.minecraft.resources.ResourceLocation id;

        Impl(net.minecraft.resources.ResourceLocation id) {
            this.id = id;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return id;
        }

        @Override
        protected Instance createInstance(com.google.gson.JsonObject json,
                                          net.minecraft.advancements.critereon.ContextAwarePredicate player,
                                          net.minecraft.advancements.critereon.DeserializationContext context) {
            List<String> kinds = new ArrayList<>();
            if (json.has("kinds")) {
                for (com.google.gson.JsonElement element : json.getAsJsonArray("kinds")) kinds.add(element.getAsString());
            }
            net.minecraft.advancements.critereon.ItemPredicate item = json.has("item")
                    ? net.minecraft.advancements.critereon.ItemPredicate.fromJson(json.get("item")) : null;
            net.minecraft.advancements.critereon.MinMaxBounds.Doubles value =
                    net.minecraft.advancements.critereon.MinMaxBounds.Doubles.fromJson(json.get("value"));
            return new Instance(id, player, List.copyOf(kinds), item, value);
        }

        void fire(ServerPlayer player, String kind, ItemStack stack, double value) {
            trigger(player, instance -> instance.test(kind, stack, value));
        }

        static final class Instance extends net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance {
            private final List<String> kinds;
            private final net.minecraft.advancements.critereon.ItemPredicate item;
            private final net.minecraft.advancements.critereon.MinMaxBounds.Doubles value;

            Instance(net.minecraft.resources.ResourceLocation id,
                     net.minecraft.advancements.critereon.ContextAwarePredicate player, List<String> kinds,
                     net.minecraft.advancements.critereon.ItemPredicate item,
                     net.minecraft.advancements.critereon.MinMaxBounds.Doubles value) {
                super(id, player);
                this.kinds = kinds;
                this.item = item;
                this.value = value;
            }

            boolean test(String kind, ItemStack stack, double amount) {
                if (!Trigger.matches(kinds, kind)) return false;
                if (item != null && !item.matches(stack)) return false;
                return value.matches(amount);
            }

            @Override
            public com.google.gson.JsonObject serializeToJson(net.minecraft.advancements.critereon.SerializationContext context) {
                com.google.gson.JsonObject json = super.serializeToJson(context);
                if (!kinds.isEmpty()) {
                    com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                    kinds.forEach(array::add);
                    json.add("kinds", array);
                }
                if (item != null) json.add("item", item.serializeToJson());
                if (!value.isAny()) json.add("value", value.serializeToJson());
                return json;
            }
        }
    }
    *///?}
}
