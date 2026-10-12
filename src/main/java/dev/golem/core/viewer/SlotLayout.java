package dev.golem.core.viewer;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Where a recipe's ingredients go in its category: JEI's {@code IRecipeLayoutBuilder} in a form every viewer can
 * read. Positions are the item's top-left corner inside the category, as in JEI; a slot's background is drawn at an
 * offset from it (JEI's frames sit at {@code -1, -1}).
 */
public interface SlotLayout {

    /** Adds a slot holding the given role's ingredient at {@code (x, y)}. */
    Slot addSlot(Role role, int x, int y);

    /** One slot: its alternatives cycle, as in both viewers. */
    interface Slot {

        /** The alternatives this slot cycles through. */
        Slot addItemStacks(List<ItemStack> stacks);

        default Slot addItemStack(ItemStack stack) {
            return addItemStacks(List.of(stack));
        }

        /** A frame drawn under the slot at {@code (x + dx, y + dy)}. */
        Slot setBackground(Drawable background, int dx, int dy);

        /** The viewer's standard slot frame (JEI's {@code setStandardSlotBackground}). */
        default Slot setStandardSlotBackground() {
            return setBackground(Drawable.slot(), -1, -1);
        }

        /** Extra tooltip lines for the stack the slot is showing (JEI's rich tooltip callback). */
        Slot addTooltipCallback(TooltipCallback callback);
    }

    /** Adds lines under a slot's tooltip; {@code shown} is the alternative on display. */
    @FunctionalInterface
    interface TooltipCallback {
        void addLines(ItemStack shown, List<Component> tooltip);
    }
}
