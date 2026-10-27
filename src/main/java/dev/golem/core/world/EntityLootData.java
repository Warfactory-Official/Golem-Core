package dev.golem.core.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;

/** JSON declarations for entity death tables, including the legacy uniform looting bonus. */
public abstract class EntityLootData extends DataSource<JsonObject> {

    protected EntityLootData() {
        super("loot_table/entities");
    }

    @Override
    public JsonElement encode(JsonObject row, HolderLookup.Provider registries) {
        return row;
    }

    @Override
    public JsonObject decode(JsonElement json, HolderLookup.Provider registries) {
        return json.getAsJsonObject();
    }

    /** Every 1.7 creeper also drops a music disc when a skeleton kills it ({@code EntityCreeper.onDeath}). */
    protected final void creeper(String id, JsonObject... pools) {
        JsonObject disc = new JsonObject();
        disc.addProperty("type", "minecraft:tag");
        disc.addProperty("name", "minecraft:creeper_drop_music_discs");
        disc.addProperty("expand", true);
        JsonObject skeleton = new JsonObject();
        skeleton.addProperty("type", "#minecraft:skeletons");
        JsonObject[] all = new JsonObject[pools.length + 1];
        System.arraycopy(pools, 0, all, 0, pools.length);
        all[pools.length] = pool(disc, entityProperties("attacker", skeleton));
        register(id, table(all));
    }

    protected static JsonObject table(JsonObject... pools) {
        JsonArray list = new JsonArray();
        for (JsonObject pool : pools) list.add(pool);
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:entity");
        table.add("pools", list);
        return table;
    }

    protected static JsonObject pool(JsonObject entry, JsonObject... conditions) {
        JsonArray entries = new JsonArray();
        entries.add(entry);
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        pool.add("entries", entries);
        if (conditions.length > 0) pool.add("conditions", array(conditions));
        return pool;
    }

    protected static JsonObject item(String name, JsonObject... functions) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", name);
        if (functions.length > 0) entry.add("functions", array(functions));
        return entry;
    }

    /** The first child when {@code condition} holds, else the second. */
    protected static JsonObject alternatives(JsonObject first, JsonObject condition, JsonObject second) {
        first.add("conditions", array(condition));
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:alternatives");
        entry.add("children", array(first, second));
        return entry;
    }

    protected static JsonObject setCount(JsonObject count) {
        JsonObject function = new JsonObject();
        function.addProperty("function", "minecraft:set_count");
        function.add("count", count);
        return function;
    }

    /** 1.7's {@code rand(looting + 1)}, added to the count. */
    protected static JsonObject addLooting(boolean playerOnly) {
        JsonObject function = setCount(uniform(constant(0), looting(1, 0, playerOnly)));
        function.addProperty("add", true);
        return function;
    }

    protected static JsonObject constant(int value) {
        JsonObject number = new JsonObject();
        number.addProperty("type", "minecraft:constant");
        number.addProperty("value", value);
        return number;
    }

    protected static JsonObject uniform(JsonObject min, JsonObject max) {
        JsonObject number = new JsonObject();
        number.addProperty("type", "minecraft:uniform");
        number.add("min", min);
        number.add("max", max);
        return number;
    }

    protected static JsonObject looting(int factor, int add, boolean playerOnly) {
        JsonObject number = new JsonObject();
        number.addProperty("type", "golemcore:looting");
        if (factor != 1) number.addProperty("factor", factor);
        if (add != 0) number.addProperty("add", add);
        if (playerOnly) number.addProperty("player_only", true);
        return number;
    }

    protected static JsonObject entityProperties(String entity, JsonObject predicate) {
        var condition = new JsonObject();
        condition.addProperty("condition", "minecraft:entity_properties");
        condition.addProperty("entity", entity);
        condition.add("predicate", predicate);
        return condition;
    }

    protected static JsonObject killedByPlayer() {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:killed_by_player");
        return condition;
    }

    protected static JsonObject not(JsonObject term) {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:inverted");
        condition.add("term", term);
        return condition;
    }

    protected static JsonObject onFire() {
        JsonObject flags = new JsonObject();
        flags.addProperty("is_on_fire", true);
        JsonObject predicate = new JsonObject();
        predicate.add("flags", flags);
        return entityProperties("this", predicate);
    }

    /** 1.7 {@code rand.nextInt(n) == 0} as {@code random_chance}: within a float's rounding of {@code 1 / n}. */
    protected static JsonObject randomChance(float chance) {
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:random_chance");
        condition.addProperty("chance", chance);
        return condition;
    }

    /** {@code nextInt(200) - looting < 5}: a uniform 0..199 at most {@code 4 + looting}, looting from a player killer. */
    protected static JsonObject rareDrop() {
        JsonObject range = new JsonObject();
        range.add("max", looting(1, 4, true));
        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:value_check");
        condition.add("value", uniform(constant(0), constant(199)));
        condition.add("range", range);
        return condition;
    }

    protected static JsonArray array(JsonObject... values) {
        JsonArray array = new JsonArray();
        for (JsonObject value : values) array.add(value);
        return array;
    }
}
