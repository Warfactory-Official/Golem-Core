package dev.golem.core.emi;

//? if emi {
/*import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.config.FluidUnit;
import dev.golem.core.fluid.FluidUnits;
import dev.golem.core.viewer.Drawable;
import dev.golem.core.viewer.Role;
import dev.golem.core.viewer.SlotLayout;
import dev.golem.core.viewer.ViewerCategory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/^*
 * One recipe of a {@link ViewerCategory} as an EMI recipe. {@code setRecipe} runs once into a recording layout; the
 * recorded slots are drawn at JEI's positions (EMI's slot widget includes the frame, hence {@code x - 1, y - 1}).
 * {@link #items} is the same recipe with its item inputs only, in slot order: EMI's fill maps inputs to menu slots by
 * position and refuses a recipe holding anything it cannot find in the inventory, such as a fluid.
 ^/
final class ViewerRecipe<T> implements EmiRecipe {

    private static final class RecordedSlot implements SlotLayout.Slot {
        final Role role;
        final int x, y;
        final List<EmiStack> stacks = new ArrayList<>();
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
            for (ItemStack stack : list) if (stack != null && !stack.isEmpty()) stacks.add(EmiStack.of(stack));
            return this;
        }

        @Override
        public SlotLayout.Slot addFluidStack(Fluid fluid, long milliBuckets) {
            stacks.add(EmiStack.of(fluid, milliBuckets * FluidUnit.BUCKET / FluidUnits.MB_PER_BUCKET));
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

        EmiIngredient ingredient() {
            if (stacks.isEmpty()) return EmiStack.EMPTY;
            return stacks.size() == 1 ? stacks.get(0) : EmiIngredient.of(stacks);
        }

        boolean holdsFluid() {
            for (EmiStack stack : stacks) if (stack.getItemStack().isEmpty()) return true;
            return false;
        }
    }

    /^* A slot whose extra tooltip lines depend on the alternative on display. ^/
    private static final class ViewerSlot extends SlotWidget {
        private final RecordedSlot slot;

        ViewerSlot(RecordedSlot slot) {
            super(slot.ingredient(), slot.x - 1, slot.y - 1);
            this.slot = slot;
        }

        @Override
        protected void addSlotTooltip(List<ClientTooltipComponent> list) {
            if (!slot.tooltips.isEmpty() && !slot.stacks.isEmpty()) {
                // the alternative EMI's list ingredient is drawing this second
                ItemStack shown = slot.stacks.get((int) (System.currentTimeMillis() / 1000 % slot.stacks.size())).getItemStack();
                if (!shown.isEmpty()) {
                    List<Component> lines = new ArrayList<>();
                    for (SlotLayout.TooltipCallback callback : slot.tooltips) callback.addLines(shown, lines);
                    for (Component line : lines) list.add(ClientTooltipComponent.create(line.getVisualOrderText()));
                }
            }
            super.addSlotTooltip(list);
        }
    }

    private final ViewerCategory<T> category;
    private final EmiRecipeCategory emiCategory;
    private final T recipe;
    private final ResourceLocation id;
    private final List<RecordedSlot> slots = new ArrayList<>();
    private final List<EmiIngredient> inputs = new ArrayList<>();
    private final List<EmiIngredient> itemInputs = new ArrayList<>();
    private final List<EmiIngredient> catalysts = new ArrayList<>();
    private final List<EmiStack> outputs = new ArrayList<>();

    ViewerRecipe(ViewerCategory<T> category, EmiRecipeCategory emiCategory, T recipe, ResourceLocation id) {
        this.category = category;
        this.emiCategory = emiCategory;
        this.recipe = recipe;
        this.id = id;
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
        for (RecordedSlot slot : slots) {
            if (slot.role == Role.INPUT && !slot.holdsFluid()) itemInputs.add(slot.ingredient());
        }
        List<RecordedSlot> all = new ArrayList<>(slots);
        all.addAll(invisible);
        for (RecordedSlot slot : all) {
            if (slot.stacks.isEmpty()) continue;
            switch (slot.role) {
                case INPUT -> inputs.add(slot.ingredient());
                case OUTPUT -> outputs.addAll(slot.stacks);
                case CRAFTING_STATION -> catalysts.add(slot.ingredient());
            }
        }
    }

    ViewerCategory<T> category() {
        return category;
    }

    T recipe() {
        return recipe;
    }

    /^* This recipe with its item inputs alone, for EMI's fill. ^/
    EmiRecipe items() {
        ViewerRecipe<T> self = this;
        return new EmiRecipe() {
            @Override public EmiRecipeCategory getCategory() { return self.getCategory(); }
            @Override public ResourceLocation getId() { return self.getId(); }
            @Override public List<EmiIngredient> getInputs() { return self.itemInputs; }
            @Override public List<EmiStack> getOutputs() { return self.outputs; }
            @Override public int getDisplayWidth() { return self.getDisplayWidth(); }
            @Override public int getDisplayHeight() { return self.getDisplayHeight(); }
            @Override public void addWidgets(WidgetHolder widgets) { self.addWidgets(widgets); }
        };
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return emiCategory;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return inputs;
    }

    @Override
    public List<EmiIngredient> getCatalysts() {
        return catalysts;
    }

    @Override
    public List<EmiStack> getOutputs() {
        return outputs;
    }

    @Override
    public int getDisplayWidth() {
        return category.width();
    }

    @Override
    public int getDisplayHeight() {
        return category.height();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addDrawable(0, 0, category.width(), category.height(),
                (graphics, mouseX, mouseY, delta) -> category.draw(recipe, graphics, mouseX, mouseY));
        for (RecordedSlot slot : slots) {
            Drawable background = slot.background;
            boolean standard = background == Drawable.slot();
            if (background != null && !standard) {
                widgets.addDrawable(slot.x + slot.backgroundX, slot.y + slot.backgroundY, background.width(), background.height(),
                        (graphics, mouseX, mouseY, delta) -> background.draw(graphics, 0, 0));
            }
            SlotWidget widget = widgets.add(new ViewerSlot(slot)).drawBack(standard);
            // an output slot with the recipe as context is what shows the id and offers the recipe tree
            if (slot.role == Role.OUTPUT) widget.recipeContext(this);
        }
    }
}
*///?}
