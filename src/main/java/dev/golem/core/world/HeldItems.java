package dev.golem.core.world;

import dev.golem.core.data.GolemComponent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.item.ItemStack;

/**
 * Item data that is no new item for the first-person hand. The hand lowers and raises the held item whenever its stack
 * stops matching the one shown; data that any item may carry and that changes while held (a dose an item soaks up)
 * would re-equip it on every change. 1.7 compared only the item and its damage.
 */
public final class HeldItems {

    private static final List<GolemComponent<?>> IGNORED = new CopyOnWriteArrayList<>();

    private HeldItems() {}

    /** A change of {@code component} alone no longer re-equips any held item. */
    public static void ignoreComponent(GolemComponent<?> component) {
        if (!IGNORED.contains(component)) IGNORED.add(component);
    }

    /** Whether two stacks of one item differ in ignored components only (and differ in at least one of them). */
    public static boolean sameApartFromIgnored(ItemStack shown, ItemStack held) {
        if (IGNORED.isEmpty() || shown.getItem() != held.getItem()) return false;
        boolean involved = false;
        for (GolemComponent<?> component : IGNORED) involved |= component.has(shown) || component.has(held);
        if (!involved) return false;
        ItemStack a = shown.copy(), b = held.copy();
        for (GolemComponent<?> component : IGNORED) {
            component.remove(a);
            component.remove(b);
        }
        return ItemStack.matches(a, b);
    }
}
