package dev.golem.core.world;

import dev.golem.core.Ids;
import dev.golem.core.hook.Hook;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.phys.Vec3;

/**
 * Adding a mod's items to another table's chest loot (a vanilla dungeon, a mineshaft) the same way on
 * every loader and version, and rolling a table to see what it gives.
 *
 * <p>Each injection is one extra pool appended to the named table when it loads: {@code rolls} draws, each
 * picking one of the entries or, with weight {@code emptyWeight}, nothing. The empty weight reproduces 1.7's
 * "add to the shared weighted list": an item of weight {@code w} added to a list of total weight {@code W} comes
 * out of each draw with probability {@code w / (W + w)}, which a pool of that item plus {@code W} of nothing gives.
 *
 * <p>NeoForge and Forge fire a {@code LootTableLoadEvent} carrying the built table, which takes a named pool;
 * Fabric's {@code LootTableEvents.MODIFY} hands over the table's builder, with a different callback shape from
 * 1.20.5 on.
 */
public final class ChestLoot {

    private ChestLoot() {}

    /** One possible result of a draw: {@code min..max} of the item, chosen with {@code weight}. */
    public record Entry(Supplier<? extends Item> item, int min, int max, int weight) {
        public Entry {
            if (min < 1 || max < min) throw new IllegalArgumentException("count " + min + ".." + max);
            if (weight < 1) throw new IllegalArgumentException("weight " + weight);
        }
    }

    private record Injection(String table, String name, int rollsMin, int rollsMax, int emptyWeight,
                             List<Entry> entries) {}

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final Hook<Injection> INJECTIONS = Hook.notifying();
    private static boolean hooked;

    /**
     * Appends a pool to the loot table {@code table} (a full id such as
     * {@code minecraft:chests/simple_dungeon}) whenever it loads. {@code name} must be unique within the
     * table. Call during common initialisation; the item suppliers are resolved only when tables load.
     */
    public static synchronized void inject(String table, String name, int rollsMin, int rollsMax, int emptyWeight,
                                           List<Entry> entries) {
        if (rollsMin < 1 || rollsMax < rollsMin) throw new IllegalArgumentException("rolls " + rollsMin + ".." + rollsMax);
        if (emptyWeight < 0) throw new IllegalArgumentException("empty weight " + emptyWeight);
        if (entries.isEmpty()) throw new IllegalArgumentException(table + " injection " + name + " has no entries");
        Ids.parse(table);
        INJECTIONS.register(new Injection(table, name, rollsMin, rollsMax, emptyWeight, List.copyOf(entries)));
        if (!hooked) {
            hooked = true;
            hook();
        }
    }

    /** Rolls a chest loot table once at {@code origin}, as a freshly opened chest would. */
    public static List<ItemStack> roll(ServerLevel level, String table, Vec3 origin) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .create(LootContextParamSets.CHEST);
        //? if >=1.20.5 {
        LootTable loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, Ids.parse(table)));
        //?} else {
        /*LootTable loot = level.getServer().getLootData().getLootTable(Ids.parse(table));
        *///?}
        return new ArrayList<>(loot.getRandomItems(params));
    }

    private static void apply(String table, Consumer<LootPool.Builder> sink) {
        for (Injection injection : INJECTIONS) {
            if (injection.table.equals(table)) sink.accept(pool(injection));
        }
    }

    private static LootPool.Builder pool(Injection injection) {
        LootPool.Builder pool = LootPool.lootPool().setRolls(range(injection.rollsMin, injection.rollsMax));
        for (Entry entry : injection.entries) {
            LootPoolSingletonContainer.Builder<?> item = LootItem.lootTableItem(entry.item.get()).setWeight(entry.weight);
            if (entry.min != 1 || entry.max != 1) item.apply(SetItemCountFunction.setCount(range(entry.min, entry.max)));
            pool.add(item);
        }
        if (injection.emptyWeight > 0) pool.add(EmptyLootItem.emptyItem().setWeight(injection.emptyWeight));
        //? if forge || neoforge {
        pool.name(injection.name);
        //?}
        return pool;
    }

    private static NumberProvider range(int min, int max) {
        return min == max ? ConstantValue.exactly(min) : UniformGenerator.between(min, max);
    }

    private static void hook() {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.LootTableLoadEvent event) ->
                        apply(event.getName().toString(), pool -> event.getTable().addPool(pool.build())));
        //?}
        //? if forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.LootTableLoadEvent event) ->
                        apply(event.getName().toString(), pool -> event.getTable().addPool(pool.build())));
        *///?}
        //? if fabric && >=1.20.5 {
        /*net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((key, builder, source, registries) ->
                apply(Ids.keyId(key).toString(), builder::withPool));
        *///?} elif fabric {
        /*net.fabricmc.fabric.api.loot.v2.LootTableEvents.MODIFY.register((resources, manager, id, builder, source) ->
                apply(id.toString(), builder::withPool));
        *///?}
    }
}
