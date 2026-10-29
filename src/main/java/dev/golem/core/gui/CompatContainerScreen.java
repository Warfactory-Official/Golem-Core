package dev.golem.core.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
 *///?}

/**
 * A container screen that behaves like 26.1's on every version.
 *
 * <p>Three differences in {@code AbstractContainerScreen} are invisible at compile time (the overrides
 * bind, the screen draws) and surface only as a misplaced label and tooltips that never appear:
 *
 * <ul>
 *   <li><b>26.1 has a constructor taking the screen's size</b>; 1.21.1 has only the three-argument one
 *       and expects subclasses to assign {@code imageWidth}/{@code imageHeight} afterwards.</li>
 *   <li><b>The inventory label's Y is derived at a different moment.</b> 26.1 computes
 *       {@code inventoryLabelY = imageHeight - 94} from that constructor <i>argument</i>. 1.21.1 computes
 *       it from the {@code imageHeight} <i>field</i>, which is still the 166 default at that point, so the
 *       label is stranded at 72. The sized constructor here fixes both.</li>
 *   <li><b>The tooltip hook is engine-called on 26.1 and subclass-called below it.</b> 26.1's
 *       {@code extractRenderState} always invokes {@code extractTooltip}. In 1.21.1 nothing in
 *       {@code AbstractContainerScreen} calls {@code renderTooltip}; every vanilla screen that
 *       wants tooltips calls it from its own {@code render}. A port that only renames the override
 *       compiles to a method nobody invokes.</li>
 *   <li><b>On 26.1 that hook is already occupied.</b> {@code extractTooltip} draws the hovered slot's
 *       <em>item</em> tooltip; below 26.1 it is empty. A screen that overrides it to add a gauge tooltip
 *       keeps item tooltips on five targets and loses them on the sixth. Subclasses override
 *       {@link #extractOwnTooltip} instead.</li>
 * </ul>
 *
 * <p>Extend this instead of {@code AbstractContainerScreen} and all three behave as they do on 26.1.
 */
public abstract class CompatContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    /**
     * Direct field for JEI's pre-key focus probe; it cannot discover EditBox instances inside arrays.
     */
    protected EditBox focusedEditBoxForInput;

    protected CompatContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * The sized constructor, which 26.1 has natively and older versions reproduce field by field.
     */
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

    /**
     * The 26.1 background hook, backported for subclasses.
     */
    public void extractBackground(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
             *///?}
            int mouseX, int mouseY, float partialTick) {
    }

    /**
     * The 26.1 label hook, backported for subclasses. The default is vanilla's: the title at
     * ({@code titleLabelX}, {@code titleLabelY}) and "Inventory" at ({@code inventoryLabelX},
     * {@code inventoryLabelY}), dark grey, no shadow, on every version. An empty body would be
     * wrong on 26.1, where it overrides vanilla's labels away.
     */
    protected void extractLabels(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
             *///?}
            int mouseX, int mouseY) {
        //? if >=26.1 {
        super.extractLabels(graphics, mouseX, mouseY);
        //?} else {
        /*super.renderLabels(graphics, mouseX, mouseY);
         *///?}
    }

    /**
     * This screen's <em>own</em> tooltips: the ones for gauges, tanks, bars and other painted regions.
     *
     * <p>Not named {@code extractTooltip}: on 26.1 that is the vanilla method that draws the hovered
     * <em>slot's item</em> tooltip, so overriding it without calling super deletes item tooltips for
     * the whole screen, on one of the six targets only. {@code extractTooltip} is {@code final} here so
     * such an override fails to compile.
     *
     * <p>Called <b>before</b> the item tooltip. 26.1's {@code setTooltipForNextFrame} keeps the
     * <em>first</em> tooltip set in a frame, so a screen can override the item tooltip for a region that
     * overlaps a slot, while a region that does not overlap leaves the item's alone.
     */
    protected void extractOwnTooltip(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
             *///?}
            int mouseX, int mouseY) {
    }

    //? if >=26.1 {

    /**
     * Runs this screen's own tooltips, then vanilla's hovered-item one. See {@link #extractOwnTooltip}.
     */
    @Override
    protected final void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        this.extractOwnTooltip(graphics, mouseX, mouseY);
        super.extractTooltip(graphics, mouseX, mouseY);
    }
    //?}

    /**
     * Version-neutral click hook.
     */
    protected boolean mouseClicked(MouseInput event, boolean doubleClick) {
        return false;
    }

    /**
     * Version-neutral release hook.
     */
    protected boolean mouseReleased(MouseInput event) {
        return false;
    }

    /**
     * A drag uses the same cursor, button and deltas on every node.
     */
    protected boolean mouseDragged(MouseInput event, double deltaX, double deltaY) {
        return false;
    }

    /**
     * Version-neutral key hook.
     */
    protected boolean keyPressed(KeyInput event) {
        return false;
    }

    /**
     * Version-neutral key-release hook.
     */
    protected boolean keyReleased(KeyInput event) {
        return false;
    }

    /**
     * Version-neutral character hook.
     */
    protected boolean charTyped(CharInput event) {
        return false;
    }

    /**
     * Version-neutral wheel hook.
     */
    protected boolean mouseScrolledCompat(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    //? if >=26.1 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = this.mouseClicked(new MouseInput(event), doubleClick)
                || super.mouseClicked(event, doubleClick);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.mouseReleased(new MouseInput(event)) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return this.mouseDragged(new MouseInput(event), deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyInput input = new KeyInput(event);
        boolean handled = this.keyPressed(input) || this.keyPressedFocusedEditBox(input) || super.keyPressed(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyInput input = new KeyInput(event);
        boolean handled = this.keyReleased(input) || this.keyReleasedFocusedEditBox(input) || super.keyReleased(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        CharInput input = new CharInput(event);
        boolean handled = this.charTyped(input) || this.charTypedFocusedEditBox(input) || super.charTyped(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.mouseScrolledCompat(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY,
                scrollX, scrollY);
    }
    //?} else {
    /*@Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        this.extractLabels(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // This era draws immediately rather than deferring, so the later call wins the z-order: the
        // item tooltip first, this screen's own over it. The net effect matches 26.1's first-writer
        // rule for the only case that arises in practice, a region tooltip over a slot.
        super.renderTooltip(graphics, mouseX, mouseY);
        this.extractOwnTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = this.mouseClicked(new MouseInput(mouseX, mouseY, button), false)
                || super.mouseClicked(mouseX, mouseY, button);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.mouseReleased(new MouseInput(mouseX, mouseY, button)) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.mouseDragged(new MouseInput(mouseX, mouseY, button), deltaX, deltaY)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyInput input = new KeyInput(keyCode, scanCode, modifiers);
        boolean handled = this.keyPressed(input) || this.keyPressedFocusedEditBox(input)
                || super.keyPressed(keyCode, scanCode, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyInput input = new KeyInput(keyCode, scanCode, modifiers);
        boolean handled = this.keyReleased(input) || this.keyReleasedFocusedEditBox(input)
                || super.keyReleased(keyCode, scanCode, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        CharInput input = new CharInput(codePoint, modifiers);
        boolean handled = this.charTyped(input) || this.charTypedFocusedEditBox(input)
                || super.charTyped(codePoint, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }
    *///?}

    //? if >=1.21 && <26.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.mouseScrolledCompat(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    *///?} elif <1.21 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        return this.mouseScrolledCompat(mouseX, mouseY, 0.0D, scrollDelta)
                || super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }
    *///?}

    //? if <26.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 26.1 calls this from extractRenderState; here it is on us. After super.render so it draws over
        // the slots, matching the order the tooltip hooks run in.
        this.renderTooltip(graphics, mouseX, mouseY);
    }
    *///?}

    private EditBox focusedEditBox() {
        if (this.focusedEditBoxForInput != null && this.focusedEditBoxForInput.isActive()
                && this.focusedEditBoxForInput.isFocused()) return this.focusedEditBoxForInput;
        return this.getFocused() instanceof EditBox editBox && editBox.isActive() && editBox.isFocused()
                ? editBox : null;
    }

    @Override
    public void setFocused(GuiEventListener focused) {
        super.setFocused(focused);
        this.focusedEditBoxForInput = focused instanceof EditBox editBox ? editBox : null;
    }

    private void updateFocusedEditBox() {
        if (this.getFocused() instanceof EditBox editBox && editBox.isActive() && editBox.isFocused()) {
            this.focusedEditBoxForInput = editBox;
        } else if (this.focusedEditBoxForInput == null || !this.focusedEditBoxForInput.isFocused()) {
            this.focusedEditBoxForInput = null;
        }
    }

    private boolean keyPressedFocusedEditBox(KeyInput event) {
        EditBox editBox = this.focusedEditBox();
        if (editBox == null || !EditBoxInput.consumesKey(event.key())) return false;
        event.keyPressed(editBox);
        return true;
    }

    private boolean keyReleasedFocusedEditBox(KeyInput event) {
        EditBox editBox = this.focusedEditBox();
        if (editBox == null || !EditBoxInput.consumesKey(event.key())) return false;
        event.keyReleased(editBox);
        return true;
    }

    private boolean charTypedFocusedEditBox(CharInput event) {
        EditBox editBox = this.focusedEditBox();
        if (editBox == null) return false;
        event.charTyped(editBox);
        return true;
    }
}
