package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Block loot tables in the node's own format, for blocks minted at runtime. Item predicates became
 * component predicates in 1.20.5, which moved the silk-touch test.
 */
public final class LootJson {

    private LootJson() {}

    /** Drops the block itself when it survives the explosion that broke it. */
    public static JsonObject dropSelf(String block) {
        JsonObject entry = item(block);
        JsonArray conditions = new JsonArray();
        JsonObject survives = new JsonObject();
        survives.addProperty("condition", "minecraft:survives_explosion");
        conditions.add(survives);
        entry.add("conditions", conditions);
        return table(entry);
    }

    /** Silk touch drops {@code block}; otherwise {@code min..max} of {@code drop}, fortune-boosted, explosion-decayed. */
    public static JsonObject oreDrops(String block, String drop, int min, int max) {
        JsonObject silk = item(block);
        JsonArray conditions = new JsonArray();
        conditions.add(silkTouch());
        silk.add("conditions", conditions);

        JsonObject normal = item(drop);
        JsonArray functions = new JsonArray();
        if (min != 1 || max != 1) {
            JsonObject count = new JsonObject();
            count.addProperty("type", "minecraft:uniform");
            count.addProperty("min", min);
            count.addProperty("max", max);
            JsonObject setCount = new JsonObject();
            setCount.addProperty("function", "minecraft:set_count");
            setCount.add("count", count);
            setCount.addProperty("add", false);
            functions.add(setCount);
        }
        JsonObject bonus = new JsonObject();
        bonus.addProperty("function", "minecraft:apply_bonus");
        bonus.addProperty("enchantment", "minecraft:fortune");
        bonus.addProperty("formula", "minecraft:ore_drops");
        functions.add(bonus);
        JsonObject decay = new JsonObject();
        decay.addProperty("function", "minecraft:explosion_decay");
        functions.add(decay);
        normal.add("functions", functions);

        JsonObject alternatives = new JsonObject();
        alternatives.addProperty("type", "minecraft:alternatives");
        JsonArray children = new JsonArray();
        children.add(silk);
        children.add(normal);
        alternatives.add("children", children);
        return table(alternatives);
    }

    private static JsonObject silkTouch() {
        JsonObject levels = new JsonObject();
        levels.addProperty("min", 1);
        JsonObject enchantment = new JsonObject();
        JsonArray enchantments = new JsonArray();
        enchantments.add(enchantment);
        JsonObject predicate = new JsonObject();
        //? if >=1.20.5 {
        enchantment.addProperty("enchantments", "minecraft:silk_touch");
        enchantment.add("levels", levels);
        JsonObject predicates = new JsonObject();
        predicates.add("minecraft:enchantments", enchantments);
        predicate.add("predicates", predicates);
        //?} else {
        /*enchantment.addProperty("enchantment", "minecraft:silk_touch");
        enchantment.add("levels", levels);
        predicate.add("enchantments", enchantments);
        *///?}
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:match_tool");
        condition.add("predicate", predicate);
        return condition;
    }

    private static JsonObject item(String id) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", id);
        return entry;
    }

    private static JsonObject table(JsonObject entry) {
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1.0);
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("entries", entries);
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", pools);
        return table;
    }
}
