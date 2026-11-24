package dev.golem.core.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

//? if >=1.21.11 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}

/**
 * A plain screen with the render and input hook names used by current Minecraft.
 *
 * <p>Feature screens override the {@code extract*Compat} and input compatibility hooks rather than
 * Minecraft methods whose signatures change between releases.
 */
public abstract class CompatScreen extends Screen {

    /** Direct field for JEI's pre-key focus probe; it cannot discover EditBox instances inside arrays. */
    protected EditBox focusedEditBoxForInput;

    protected CompatScreen(Component title) {
        super(title);
    }

    protected void extractBackgroundCompat(
            //? if >=1.21.11 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphicsExtractor graphics,
            *///?}
            int mouseX, int mouseY, float partialTick) {}

    protected void extractForegroundCompat(
            //? if >=1.21.11 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphicsExtractor graphics,
            *///?}
            int mouseX, int mouseY, float partialTick) {}

    protected boolean mouseClickedCompat(MouseInput event, boolean doubleClick) { return false; }

    /** Called after vanilla widgets decline a click. */
    protected boolean mouseClickedAfterWidgets(MouseInput event, boolean doubleClick) { return false; }

    protected boolean mouseReleasedCompat(MouseInput event) { return false; }

    /** A drag with the button held; the deltas are in GUI pixels since the last call. */
    protected boolean mouseDraggedCompat(MouseInput event, double deltaX, double deltaY) { return false; }

    protected boolean keyPressedCompat(KeyInput event) { return false; }

    protected boolean keyReleasedCompat(KeyInput event) { return false; }

    protected boolean charTypedCompat(CharInput event) { return false; }

    protected boolean mouseScrolledCompat(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    /** False leaves the world behind the screen neither blurred nor darkened, as 1.20.1 screens do by default. */
    protected boolean menuBackground() {
        return true;
    }

    //? if >=1.21.11 {
    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.menuBackground()) super.extractBackground(graphics, mouseX, mouseY, partialTick);
        this.extractBackgroundCompat(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractForegroundCompat(graphics, mouseX, mouseY, partialTick);
    }
    //?}

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseInput input = new MouseInput(event);
        return this.mouseClickedCompat(input, doubleClick) || super.mouseClicked(event, doubleClick)
                || this.mouseClickedAfterWidgets(input, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.mouseReleasedCompat(new MouseInput(event)) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return this.mouseDraggedCompat(new MouseInput(event), deltaX, deltaY) || super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        KeyInput input = new KeyInput(event);
        boolean handled = this.keyPressedCompat(input) || this.keyPressedFocusedEditBox(input) || super.keyPressed(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        KeyInput input = new KeyInput(event);
        boolean handled = this.keyReleasedCompat(input) || this.keyReleasedFocusedEditBox(input)
                || super.keyReleased(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        CharInput input = new CharInput(event);
        boolean handled = this.charTypedCompat(input) || this.charTypedFocusedEditBox(input)
                || super.charTyped(event);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.mouseScrolledCompat(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*/^ The 1.21.11 MouseHandler rule: the same button, on the same screen, under 250 ms after the last handled click. ^/
    private long lastClickMillis = Long.MIN_VALUE;
    private int lastClickButton = -1;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseInput input = new MouseInput(mouseX, mouseY, button);
        long now = net.minecraft.Util.getMillis();
        boolean doubleClick = lastClickButton == button && now - lastClickMillis < 250L;
        boolean handled = this.mouseClickedCompat(input, doubleClick) || super.mouseClicked(mouseX, mouseY, button)
                || this.mouseClickedAfterWidgets(input, doubleClick);
        if (handled) {
            lastClickMillis = now;
            lastClickButton = button;
        }
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.mouseReleasedCompat(new MouseInput(mouseX, mouseY, button))
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return this.mouseDraggedCompat(new MouseInput(mouseX, mouseY, button), deltaX, deltaY)
                || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyInput input = new KeyInput(keyCode, scanCode, modifiers);
        boolean handled = this.keyPressedCompat(input) || this.keyPressedFocusedEditBox(input)
                || super.keyPressed(keyCode, scanCode, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        KeyInput input = new KeyInput(keyCode, scanCode, modifiers);
        boolean handled = this.keyReleasedCompat(input) || this.keyReleasedFocusedEditBox(input)
                || super.keyReleased(keyCode, scanCode, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        CharInput input = new CharInput(codePoint, modifiers);
        boolean handled = this.charTypedCompat(input) || this.charTypedFocusedEditBox(input)
                || super.charTyped(codePoint, modifiers);
        this.updateFocusedEditBox();
        return handled;
    }
    *///?}

    /**
     * The background goes in {@code renderBackground}, not ahead of {@code render}.
     *
     * <p>1.20.2 moved {@code renderBackground} to the top of {@code Screen.render} and made it run the
     * menu blur, so anything already in the framebuffer when {@code super.render} is entered gets
     * blurred. A panel drawn first goes soft while text and items stay sharp, which looks like a
     * texture problem. Vanilla's container screens hang {@code renderBg} off {@code renderBackground}
     * for the same reason. 1.20.1 has neither the blur nor the call, so it keeps the plain order.
     */
    //? if >=1.21 && <26.1 {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (this.menuBackground()) super.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.extractBackgroundCompat(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.extractForegroundCompat(graphics, mouseX, mouseY, partialTick);
    }
    *///?} elif <1.21 {
    /*@Override
    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackgroundCompat(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.extractForegroundCompat(graphics, mouseX, mouseY, partialTick);
    }
    *///?}

    //? if >=1.21 && <1.21.11 {
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
