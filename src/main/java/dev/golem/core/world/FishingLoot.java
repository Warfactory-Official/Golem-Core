package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/** Fishing-category replacements shared by rod loot and other consumers that explicitly draw a category. */
public final class FishingLoot {
    private FishingLoot() {
    }

    public enum Category {
        FISH("minecraft:gameplay/fishing/fish"),
        JUNK("minecraft:gameplay/fishing/junk"),
        TREASURE("minecraft:gameplay/fishing/treasure");

        private final String table;
        private static final Category[] ALL = values();

        Category(String table) {
            this.table = table;
        }

        private LootTable table(ServerLevel level) {
            return ChestLoot.table(level, table);
        }
    }

    @FunctionalInterface
    public interface Selector {
        /** Null preserves normal loot; origin is null when the real loot context supplies no position. */
        @Nullable ItemStack select(ServerLevel level, @Nullable BlockPos origin, Category category,
                                   RandomSource random);
    }

    /** The first selector with an answer wins; an empty hook leaves every ordinary fishing consumer unchanged. */
    public static final Hook<Selector> SELECTORS = Hook.notifying();

    public static @Nullable ItemStack select(ServerLevel level, @Nullable BlockPos origin, Category category,
                                             RandomSource random) {
        for (var selector : SELECTORS) {
            var result = selector.select(level, origin, category, random);
            if (result != null) return result;
        }
        return null;
    }

    /** A loot table is immutable across a load; its mixin caches this small category tag on the table itself. */
    public static byte categoryTag(LootTable table, ServerLevel level) {
        for (var index = 0; index < Category.ALL.length; index++) {
            if (Category.ALL[index].table(level) == table) return (byte) (index + 1);
        }
        return -1;
    }

    public static @Nullable ItemStack select(ServerLevel level, @Nullable BlockPos origin, byte category,
                                             RandomSource random) {
        return select(level, origin, Category.ALL[category - 1], random);
    }
}
