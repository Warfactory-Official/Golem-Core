package dev.golem.core.rei;

import dev.architectury.fluid.FluidStack;

//? if rei {

import dev.golem.core.fluid.FluidUnits;
import dev.golem.core.viewer.Drawable;
import dev.golem.core.viewer.Role;
import dev.golem.core.viewer.SlotLayout;
import dev.golem.core.viewer.ViewerCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One recipe of a {@link ViewerCategory} as a REI display. The category's {@code setRecipe} runs once, into a
 * recording layout: its input slots are the display's inputs (what REI's uses lookup and "+" transfer read), its
 * output slots the outputs, and the recorded slots are what {@link ReiCategory} draws. Crafting-station slots are
 * drawn but are neither, as JEI keeps them apart from a recipe's ingredients.
 *
 * <p>Client-side only: REI never needs to send it anywhere (26.1's serializer is null, which REI's display history
 * and favourites accept as "not saved").
 */
public final class ReiDisplay<T> implements Display {

    /** A recorded slot. */
    static final class RecordedSlot implements SlotLayout.Slot {
        final Role role;
        final int x, y;
        final List<EntryStack<?>> entries = new ArrayList<>();
        Drawable background;
        int backgroundX, backgroundY;
        final List<SlotLayout.TooltipCallback> tooltips = new ArrayList<>();

        RecordedSlot(Role role, int x, int y) {
            this.role = role;
            this.x = x;
            this.y = y;
        }

        @Override
        public SlotLayout.Slot addItemStacks(List<ItemStack> list) {
            for (var stack : list) if (stack != null && !stack.isEmpty()) entries.add(EntryStacks.of(stack));
            return this;
        }

        @Override
        public SlotLayout.Slot addFluidStack(Fluid fluid, long milliBuckets) {
            var amount = Math.multiplyExact(milliBuckets,
                    FluidStack.bucketAmount() / FluidUnits.MB_PER_BUCKET);
            entries.add(EntryStacks.of(fluid, amount));
            return this;
        }

        @Override
        public SlotLayout.Slot setBackground(Drawable drawable, int dx, int dy) {
            background = drawable;
            backgroundX = dx;
            backgroundY = dy;
            return this;
        }

        @Override
        public SlotLayout.Slot addTooltipCallback(SlotLayout.TooltipCallback callback) {
            tooltips.add(callback);
            return this;
        }
    }

    private final ViewerCategory<T> category;
    private final CategoryIdentifier<?> id;
    private final T recipe;
    private final List<RecordedSlot> slots = new ArrayList<>();
    private final List<EntryIngredient> inputs = new ArrayList<>();
    private final List<EntryIngredient> outputs = new ArrayList<>();

    ReiDisplay(ViewerCategory<T> category, CategoryIdentifier<?> id, T recipe) {
        this.category = category;
        this.id = id;
        this.recipe = recipe;
        List<RecordedSlot> invisible = new ArrayList<>();
        category.setRecipe(new SlotLayout() {
            @Override
            public SlotLayout.Slot addSlot(Role role, int x, int y) {
                RecordedSlot slot = new RecordedSlot(role, x, y);
                slots.add(slot);
                return slot;
            }

            @Override
            public void addInvisible(Role role, List<ItemStack> stacks) {
                invisible.add((RecordedSlot) new RecordedSlot(role, 0, 0).addItemStacks(stacks));
            }
        }, recipe);
        List<RecordedSlot> all = new ArrayList<>(slots);
        all.addAll(invisible);
        for (RecordedSlot slot : all) {
            if (slot.role == Role.INPUT) inputs.add(EntryIngredient.of(slot.entries));
            else if (slot.role == Role.OUTPUT) outputs.add(EntryIngredient.of(slot.entries));
        }
    }

    public ViewerCategory<T> category() {
        return category;
    }

    public T recipe() {
        return recipe;
    }

    List<RecordedSlot> slots() {
        return Collections.unmodifiableList(slots);
    }

    @Override
    public List<EntryIngredient> getInputEntries() {
        return inputs;
    }

    @Override
    public List<EntryIngredient> getOutputEntries() {
        return outputs;
    }

    @Override
    public CategoryIdentifier<?> getCategoryIdentifier() {
        return id;
    }

    //? if >=26.1 {
    @Override
    public java.util.Optional<net.minecraft.resources.Identifier> getDisplayLocation() {
        return java.util.Optional.empty();
    }

    @Override
    public me.shedaniel.rei.api.common.display.DisplaySerializer<? extends Display> getSerializer() {
        return null;
    }
    //?}
}
//?}
