package dev.golem.core.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

/**
 * A plain screen with the render and input hook names used by current Minecraft.
 *
 * <p>Feature screens override the {@code extract*Compat} and input compatibility hooks rather than
 * Minecraft methods whose signatures change between releases.
 */
public abstract class CompatScreen extends Screen {

    protected CompatScreen(Component title) {
        super(title);
    }

    protected void extractBackgroundCompat(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int mouseX, int mouseY, float partialTick) {}

    protected void extractForegroundCompat(
            //? if >=26.1 {
            GuiGraphicsExtractor graphics,
            //?} else {
            /*GuiGraphics graphics,
            *///?}
            int mouseX, int mouseY, float partialTick) {}

    protected boolean mouseClickedCompat(MouseInput event, boolean doubleClick) { return false; }

    /** Called after vanilla widgets decline a click. */
    protected boolean mouseClickedAfterWidgets(MouseInput event, boolean doubleClick) { return false; }

    protected boolean mouseReleasedCompat(MouseInput event) { return false; }

    protected boolean keyPressedCompat(KeyInput event) { return false; }

    protected boolean mouseScrolledCompat(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        this.extractBackgroundCompat(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractForegroundCompat(graphics, mouseX, mouseY, partialTick);
    }

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
    public boolean keyPressed(KeyEvent event) {
        return this.keyPressedCompat(new KeyInput(event)) || super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.mouseScrolledCompat(mouseX, mouseY, scrollX, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackgroundCompat(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.extractForegroundCompat(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseInput input = new MouseInput(mouseX, mouseY, button);
        return this.mouseClickedCompat(input, false) || super.mouseClicked(mouseX, mouseY, button)
                || this.mouseClickedAfterWidgets(input, false);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.mouseReleasedCompat(new MouseInput(mouseX, mouseY, button))
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.keyPressedCompat(new KeyInput(keyCode, scanCode, modifiers))
                || super.keyPressed(keyCode, scanCode, modifiers);
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
}
