package dev.golem.core.rei;

//? if rei {

import dev.golem.core.gui.GuiDraw;
import dev.golem.core.viewer.Drawable;
import dev.golem.core.viewer.SlotLayout;
import dev.golem.core.viewer.ViewerCategory;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Slot;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link ViewerCategory} as a REI display category: REI's recipe panel with the category drawn inside it at
 * {@link #PADDING}, the category's own background and extras first, then each recorded slot as a REI slot widget at the
 * same position JEI gives it. A slot with the standard frame keeps REI's own slot background; any other frame is drawn
 * by the category's drawable and REI's is turned off, so a category looks as it does in JEI.
 */
final class ReiCategory<T> implements DisplayCategory<ReiDisplay<T>> {

    /** Gap between REI's panel edge and the category's drawing. */
    static final int PADDING = 4;

    private final ViewerCategory<T> category;
    private final CategoryIdentifier<ReiDisplay<T>> id;
    private final Renderer icon;

    ReiCategory(ViewerCategory<T> category, CategoryIdentifier<ReiDisplay<T>> id) {
        this.category = category;
        this.id = id;
        this.icon = EntryStacks.of(category.icon());
    }

    @Override
    public CategoryIdentifier<? extends ReiDisplay<T>> getCategoryIdentifier() {
        return id;
    }

    @Override
    public Component getTitle() {
        return category.title();
    }

    @Override
    public Renderer getIcon() {
        return icon;
    }

    @Override
    public int getDisplayWidth(ReiDisplay<T> display) {
        return category.width() + 2 * PADDING;
    }

    @Override
    public int getDisplayHeight() {
        return category.height() + 2 * PADDING;
    }

    @Override
    public List<Widget> setupDisplay(ReiDisplay<T> display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        int ox = bounds.x + PADDING;
        int oy = bounds.y + PADDING;
        T recipe = display.recipe();
        widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
            GuiDraw.pushMatrix(graphics);
            GuiDraw.translate(graphics, ox, oy);
            category.draw(recipe, graphics, mouseX - ox, mouseY - oy);
            GuiDraw.popMatrix(graphics);
        }));
        for (ReiDisplay.RecordedSlot recorded : display.slots()) {
            Drawable background = recorded.background;
            boolean standard = background == Drawable.slot();
            if (background != null && !standard) {
                int bx = ox + recorded.x + recorded.backgroundX;
                int by = oy + recorded.y + recorded.backgroundY;
                widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> background.draw(graphics, bx, by)));
            }
            Slot slot = Widgets.createSlot(new Point(ox + recorded.x, oy + recorded.y)).entries(entries(recorded));
            if (!standard) slot.disableBackground();
            switch (recorded.role) {
                case INPUT -> slot.markInput();
                case OUTPUT -> slot.markOutput();
                case CRAFTING_STATION -> { }
            }
            widgets.add(slot);
        }
        return widgets;
    }

    /** The slot's stacks, each carrying the slot's extra tooltip lines. */
    private static List<EntryStack<ItemStack>> entries(ReiDisplay.RecordedSlot recorded) {
        List<EntryStack<ItemStack>> entries = new ArrayList<>(recorded.stacks.size());
        for (ItemStack stack : recorded.stacks) {
            EntryStack<ItemStack> entry = EntryStacks.of(stack);
            if (!recorded.tooltips.isEmpty()) {
                entry = entry.copy().tooltipProcessor((shown, tooltip) -> {
                    List<Component> lines = new ArrayList<>();
                    for (SlotLayout.TooltipCallback callback : recorded.tooltips) callback.addLines(shown.getValue(), lines);
                    for (Component line : lines) tooltip.add(line);
                    return tooltip;
                });
            }
            entries.add(entry);
        }
        return entries;
    }
}
//?}
