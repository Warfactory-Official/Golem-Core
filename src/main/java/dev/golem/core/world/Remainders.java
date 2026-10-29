package dev.golem.core.world;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
//? if >=1.20.5 {
import dev.golem.core.registry.ComponentPatches;
import net.minecraft.core.component.DataComponents;
//?}
//? if >=26.1 {
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.UseRemainder;
//?}

/**
 * Leftovers given to another mod's item after it was built (1.7 {@code setContainerItem}), answered first by
 * {@link Compat#craftingRemainder} on every node. Use remainders join the native item components when available.
 * Handles resolve when their items are available.
 */
public final class Remainders {

    private static final List<Map.Entry<Supplier<? extends ItemLike>, Supplier<? extends ItemLike>>> ADDED =
            new CopyOnWriteArrayList<>();

    private Remainders() {
    }

    public static void add(Supplier<? extends ItemLike> item, Supplier<? extends ItemLike> remainder) {
        ADDED.add(Map.entry(item, remainder));
        //? if >=26.1 {
        ComponentPatches.add(item, components -> components.set(DataComponents.USE_REMAINDER,
                new UseRemainder(new ItemStackTemplate(remainder.get().asItem()))));
        //?}
    }

    public static Item of(Item item) {
        for (var entry : ADDED) {
            if (entry.getKey().get().asItem() == item) return entry.getValue().get().asItem();
        }
        return null;
    }
}
