package dev.golem.core.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

//? if <26.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * A container screen that behaves like 26.1's on every version.
 *
 * <p>Three differences in {@code AbstractContainerScreen} are invisible at compile time — the overrides
 * bind, the screen draws — and surface only as a misplaced label and tooltips that never appear:
 *
 * <ul>
 *   <li><b>26.1 has a constructor taking the screen's size</b>; 1.21.1 has only the three-argument one
 *       and expects subclasses to assign {@code imageWidth}/{@code imageHeight} afterwards.</li>
 *   <li><b>The inventory label's Y is derived at a different moment.</b> 26.1 computes
 *       {@code inventoryLabelY = imageHeight - 94} from that constructor <i>argument</i>, so it reflects
 *       the screen's real height. 1.21.1 computes it from the {@code imageHeight} <i>field</i>, which is
 *       still the 166 default at that point — subclasses set their real height a moment later, and the
 *       label is left stranded at 72. Supplying the sized constructor here fixes both at once.</li>
 *   <li><b>The tooltip hook is engine-called on 26.1 and subclass-called below it.</b> 26.1's
 *       {@code extractRenderState} always invokes {@code extractTooltip}. In 1.21.1 nothing in
 *       {@code AbstractContainerScreen} ever calls {@code renderTooltip} — every vanilla screen that
 *       wants tooltips calls it from its own {@code render}. A port that only renames the override
 *       therefore compiles to a method nobody invokes, and no tooltip ever appears.</li>
 * </ul>
 *
 * <p>Extend this instead of {@code AbstractContainerScreen} and all three behave as they do on 26.1.
 */
public abstract class CompatContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected CompatContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** The sized constructor, which 26.1 has natively and older versions reproduce field by field. */
    protected CompatContainerScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        //? if >=26.1 {
        super(menu, inventory, title, imageWidth, imageHeight);
        //?} else {
        /*super(menu, inventory, title);
        // Exactly what 26.1's sized constructor does, in the same order.
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = imageHeight - 94;
        *///?}
    }

    //? if <26.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 26.1 calls this from extractRenderState; here it is on us. After super.render so it draws over
        // the slots, matching the order extractTooltip runs in.
        this.renderTooltip(graphics, mouseX, mouseY);
    }
    *///?}
}
