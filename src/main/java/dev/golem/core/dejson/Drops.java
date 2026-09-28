package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Block loot tables declared in code.
 *
 * <p>A loot table is a small program, and a handful of them here really are — a destroyed reactor
 * scattering four different kinds of rubble is a thing somebody decided. The other five hundred are
 * one of six sentences repeated: this block drops itself, this block drops nothing, this slab drops
 * two when it is a double, this ore drops its mineral unless you used silk touch. Writing those out
 * as files buys nothing and costs the usual: six spellings of "drops itself" drifted into the
 * corpus because there was no single place to say it.
 *
 * <p>Each method here emits the shape vanilla's own generator emits, which is also the shape the
 * majority of the files already have: a {@code bonus_rolls} of zero, a {@code rolls} of one, and a
 * {@code random_sequence} naming the table's own id. The last is what makes a drop reproducible
 * from a world seed rather than from whatever else had drawn from the level's generator that tick.
 *
 * <p>What is <i>not</i> normalised is {@code survives_explosion}. Whether a block that is blown up
 * leaves its item behind is a decision about the block, not a spelling, so {@link #self} and
 * {@link #selfAlways} are two verbs and the caller has to pick one.
 */
public final class Drops {

    private static final String ITEM = "minecraft:item";

    private Drops() {}

    /** The common case: the block drops itself, and not if it was blown up. */
    public static JsonObject self(String namespace, String block) {
        return table(namespace, block, pool(conditions(survivesExplosion()), entry(item(namespace, block))));
    }

    /** The block drops itself even when destroyed by an explosion. */
    public static JsonObject selfAlways(String namespace, String block) {
        return table(namespace, block, pool(null, entry(item(namespace, block))));
    }

    /** The block drops nothing at all — a multiblock's filler, a machine that is only ever broken by its core. */
    public static JsonObject nothing(String namespace, String block) {
        JsonObject loot = new JsonObject();
        loot.addProperty("type", "minecraft:block");
        loot.add("pools", new JsonArray());
        loot.addProperty("random_sequence", sequence(namespace, block));
        return Data.declare(path(namespace, block), loot);
    }

    /** The block drops a different item, and not if it was blown up. */
    public static JsonObject only(String namespace, String block, String drop) {
        return table(namespace, block, pool(conditions(survivesExplosion()), entry(drop)));
    }

    /** A slab: itself, doubled when the state being broken is the double slab. */
    public static JsonObject slab(String namespace, String block) {
        JsonObject doubled = new JsonObject();
        doubled.addProperty("function", "minecraft:set_count");
        doubled.addProperty("add", false);
        doubled.addProperty("count", 2.0);
        doubled.add("conditions", conditions(blockStateProperty(item(namespace, block), "type", "double")));
        return table(namespace, block,
                pool(null, entry(item(namespace, block), functions(doubled, function("minecraft:explosion_decay")))));
    }

    /**
     * A block that keeps what it was holding: its item carries the named components over from the
     * block entity. {@code minecraft:container} is the inventory, {@code minecraft:custom_name} the
     * anvil-given name.
     */
    public static JsonObject container(String namespace, String block, String... components) {
        if (components == null || components.length == 0) {
            throw new DejsonException(item(namespace, block) + " copies no components; use self(...)");
        }
        JsonArray include = new JsonArray();
        for (String component : components) {
            Models.requireId(component, "component");
            include.add(component);
        }
        JsonObject copy = new JsonObject();
        copy.addProperty("function", "minecraft:copy_components");
        copy.addProperty("source", "block_entity");
        copy.add("include", include);
        return table(namespace, block, pool(null, entry(item(namespace, block), functions(copy))));
    }

    /** Silk touch drops the block; anything else drops nothing. Glass, and what behaves like it. */
    public static JsonObject silkTouchOnly(String namespace, String block) {
        JsonObject entry = entry(item(namespace, block));
        entry.add("conditions", conditions(silkTouch()));
        return table(namespace, block, pool(null, entry));
    }

    /** An ore: silk touch drops the ore block, anything else drops one {@code drop}, fortune applying. */
    public static JsonObject ore(String namespace, String block, String drop) {
        return silkOr(namespace, block, drop, functions(fortuneBonus(), explosionDecay()));
    }

    /** The same, dropping between {@code min} and {@code max} before fortune. */
    public static JsonObject ore(String namespace, String block, String drop, int min, int max) {
        return silkOr(namespace, block, drop, functions(count(min, max), fortuneBonus(), explosionDecay()));
    }

    /** An ore whose yield fortune does not raise — the drop is already a stack of fragments. */
    public static JsonObject oreNoFortune(String namespace, String block, String drop, int min, int max) {
        return silkOr(namespace, block, drop, functions(count(min, max), explosionDecay()));
    }

    /**
     * An ore with no silk-touch alternative, on vanilla's own ore curve: fortune multiplies by
     * {@code ore_drops} rather than adding a uniform bonus, which is the steeper of the two.
     */
    public static JsonObject oreDrops(String namespace, String block, String drop) {
        JsonObject bonus = new JsonObject();
        bonus.addProperty("function", "minecraft:apply_bonus");
        bonus.addProperty("enchantment", "minecraft:fortune");
        bonus.addProperty("formula", "minecraft:ore_drops");
        return table(namespace, block, pool(null, entry(drop, functions(bonus, explosionDecay()))));
    }

    /** The path a block's loot table lives at. */
    /**
     * The block itself, but only from the state where {@code property} is {@code value} — the lower
     * half of a door, which is the half that carries the item. The other half drops nothing.
     */
    public static JsonObject selfWhen(String namespace, String block, String property, String value) {
        JsonObject entry = entry(item(namespace, block));
        entry.add("conditions", conditions(blockStateProperty(item(namespace, block), property, value)));
        return table(namespace, block, pool(conditions(survivesExplosion()), entry));
    }

    /** {@code drop} with probability {@code chance}, if the block survives the explosion that broke it. */
    public static JsonObject chance(String namespace, String block, String drop, double chance) {
        if (!(chance > 0 && chance < 1)) {
            throw new DejsonException(item(namespace, block) + " drop chance " + chance + " is not in (0, 1)");
        }
        JsonObject random = new JsonObject();
        random.addProperty("condition", "minecraft:random_chance");
        random.addProperty("chance", chance);
        JsonObject entry = entry(drop);
        entry.add("conditions", conditions(random));
        return table(namespace, block, pool(conditions(survivesExplosion()), entry));
    }

    /**
     * A fixed haul: one pool per stack, each always dropping exactly that many. For a wreck that breaks
     * into its materials rather than into itself.
     */
    public static JsonObject stacks(String namespace, String block, Object... dropThenCount) {
        if (dropThenCount.length == 0 || dropThenCount.length % 2 != 0) {
            throw new DejsonException(item(namespace, block) + " stacks() takes drop/count pairs");
        }
        JsonArray pools = new JsonArray();
        for (int index = 0; index < dropThenCount.length; index += 2) {
            String drop = (String) dropThenCount[index];
            int count = (Integer) dropThenCount[index + 1];
            if (count < 1) {
                throw new DejsonException(drop + " count " + count + " drops nothing");
            }
            JsonObject setCount = new JsonObject();
            setCount.addProperty("function", "minecraft:set_count");
            setCount.addProperty("count", count);
            pools.add(pool(null, entry(drop, functions(setCount))));
        }
        JsonObject loot = new JsonObject();
        loot.addProperty("type", "minecraft:block");
        loot.add("pools", pools);
        loot.addProperty("random_sequence", sequence(namespace, block));
        return Data.declare(path(namespace, block), loot);
    }

    public static String path(String namespace, String block) {
        Models.requireId(item(namespace, block), "block");
        return "data/" + namespace + "/loot_table/blocks/" + block + ".json";
    }

    private static JsonObject silkOr(String namespace, String block, String drop, JsonArray dropFunctions) {
        JsonObject silk = new JsonObject();
        silk.addProperty("type", ITEM);
        silk.add("conditions", conditions(silkTouch()));
        silk.addProperty("name", item(namespace, block));

        JsonArray children = new JsonArray();
        children.add(silk);
        children.add(entry(drop, dropFunctions));

        JsonObject alternatives = new JsonObject();
        alternatives.addProperty("type", "minecraft:alternatives");
        alternatives.add("children", children);
        return table(namespace, block, pool(null, alternatives));
    }

    private static JsonObject table(String namespace, String block, JsonObject pool) {
        JsonArray pools = new JsonArray();
        pools.add(pool);
        JsonObject loot = new JsonObject();
        loot.addProperty("type", "minecraft:block");
        loot.add("pools", pools);
        loot.addProperty("random_sequence", sequence(namespace, block));
        return Data.declare(path(namespace, block), loot);
    }

    private static JsonObject pool(JsonArray poolConditions, JsonObject... entries) {
        JsonObject pool = new JsonObject();
        pool.addProperty("bonus_rolls", 0.0);
        pool.addProperty("rolls", 1.0);
        if (poolConditions != null) {
            pool.add("conditions", poolConditions);
        }
        JsonArray list = new JsonArray();
        for (JsonObject entry : entries) {
            list.add(entry);
        }
        pool.add("entries", list);
        return pool;
    }

    private static JsonObject entry(String drop) {
        Models.requireId(drop, "drop");
        JsonObject entry = new JsonObject();
        entry.addProperty("type", ITEM);
        entry.addProperty("name", drop);
        return entry;
    }

    private static JsonObject entry(String drop, JsonArray entryFunctions) {
        JsonObject entry = entry(drop);
        entry.add("functions", entryFunctions);
        return entry;
    }

    private static JsonArray functions(JsonObject... list) {
        JsonArray functions = new JsonArray();
        for (JsonObject function : list) {
            functions.add(function);
        }
        return functions;
    }

    private static JsonArray conditions(JsonObject... list) {
        JsonArray array = new JsonArray();
        for (JsonObject condition : list) {
            array.add(condition);
        }
        return array;
    }

    private static JsonObject function(String name) {
        JsonObject function = new JsonObject();
        function.addProperty("function", name);
        return function;
    }

    private static JsonObject explosionDecay() {
        return function("minecraft:explosion_decay");
    }

    private static JsonObject count(int min, int max) {
        if (min < 0 || max < min) {
            throw new DejsonException("a drop count of " + min + ".." + max + " is not a range");
        }
        JsonObject uniform = new JsonObject();
        uniform.addProperty("type", "minecraft:uniform");
        uniform.addProperty("min", (double) min);
        uniform.addProperty("max", (double) max);
        JsonObject setCount = new JsonObject();
        setCount.addProperty("function", "minecraft:set_count");
        setCount.addProperty("add", false);
        setCount.add("count", uniform);
        return setCount;
    }

    private static JsonObject fortuneBonus() {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("bonusMultiplier", 1);
        JsonObject bonus = new JsonObject();
        bonus.addProperty("function", "minecraft:apply_bonus");
        bonus.addProperty("enchantment", "minecraft:fortune");
        bonus.addProperty("formula", "minecraft:uniform_bonus_count");
        bonus.add("parameters", parameters);
        return bonus;
    }

    private static JsonObject survivesExplosion() {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");
        return condition;
    }

    private static JsonObject silkTouch() {
        JsonObject levels = new JsonObject();
        levels.addProperty("min", 1);
        JsonObject enchantment = new JsonObject();
        enchantment.addProperty("enchantments", "minecraft:silk_touch");
        enchantment.add("levels", levels);
        JsonArray enchantments = new JsonArray();
        enchantments.add(enchantment);
        JsonObject predicates = new JsonObject();
        predicates.add("minecraft:enchantments", enchantments);
        JsonObject predicate = new JsonObject();
        predicate.add("predicates", predicates);
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:match_tool");
        condition.add("predicate", predicate);
        return condition;
    }

    private static JsonObject blockStateProperty(String block, String property, String value) {
        JsonObject properties = new JsonObject();
        properties.addProperty(property, value);
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:block_state_property");
        condition.addProperty("block", block);
        condition.add("properties", properties);
        return condition;
    }

    private static String item(String namespace, String block) {
        return namespace + ":" + block;
    }

    private static String sequence(String namespace, String block) {
        return namespace + ":blocks/" + block;
    }
}
