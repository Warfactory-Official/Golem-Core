package dev.golem.core.world;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * Leftovers given to another mod's item after it was built (1.7 {@code setContainerItem}), answered first by
 * {@link Compat#craftingRemainder} on every node. Handles resolve on lookup.
 */
public final class Remainders {

    private static final List<Map.Entry<Supplier<? extends ItemLike>, Supplier<? extends ItemLike>>> ADDED =
            new CopyOnWriteArrayList<>();

    private Remainders() {}

    public static void add(Supplier<? extends ItemLike> item, Supplier<? extends ItemLike> remainder) {
        ADDED.add(Map.entry(item, remainder));
    }

    public static Item of(Item item) {
        for (var entry : ADDED) {
            if (entry.getKey().get().asItem() == item) return entry.getValue().get().asItem();
        }
        return null;
    }
}
