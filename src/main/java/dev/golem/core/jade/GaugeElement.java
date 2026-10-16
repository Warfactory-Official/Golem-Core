package dev.golem.core.jade;

//? if jade {
import dev.golem.core.gui.GuiDraw;
import dev.golem.core.info.Gauge;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ui.IDisplayHelper;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import snownee.jade.api.ui.ResizeableElement;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;
*///?}

/** A {@link Gauge} as a Jade bar: a dark box filled from the left with the gauge's colour ramp, its text on top. */
final class GaugeElement extends
        //? if >=26.1 {
        ResizeableElement {
        //?} else {
        /*Element {
        *///?}

    private static final int HEIGHT = 12;

    private final Gauge gauge;
    private final Component text;
    private final int minWidth;

    GaugeElement(Gauge gauge, Component text) {
        this.gauge = gauge;
        this.text = text;
        minWidth = Math.max(100, Minecraft.getInstance().font.width(text) + 8);
        //? if >=26.1 {
        width = minWidth;
        height = HEIGHT;
        setFlexGrow(1);
        //?}
    }

    static Component text(Gauge gauge) {
        IDisplayHelper display = IDisplayHelper.get();
        String amount = display.humanReadableNumber(gauge.value(), gauge.unit(), false);
        if (gauge.max() > 0) amount += " / " + display.humanReadableNumber(gauge.max(), gauge.unit(), false);
        return gauge.label().isEmpty() ? Component.literal(amount)
                : Component.translatable(gauge.label()).append(": " + amount);
    }

    //? if >=26.1 {
    @Override
    public Component getNarration() {
        return text;
    }

    @Override
    public void setFreeSpace(int width, int height) {
        this.width = width;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        draw(graphics, getX(), getY(), width);
    }

    private void draw(GuiGraphicsExtractor graphics, int x, int y, int w) {
    //?} else {
    /*@Override
    public Vec2 getSize() {
        return new Vec2(minWidth, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        draw(graphics, (int) x, (int) y, (int) (maxX - x));
    }

    private void draw(GuiGraphics graphics, int x, int y, int w) {
    *///?}
        GuiDraw.fill(graphics, x, y, x + w, y + HEIGHT, 0xFF505050);
        GuiDraw.fill(graphics, x + 1, y + 1, x + w - 1, y + HEIGHT - 1, 0xFF161616);
        int inner = w - 2;
        float ratio = Math.min(Math.max((float) gauge.value() / gauge.max(), 0F), 1F);
        int filled = Math.round(inner * ratio);
        for (int i = 0; i < filled; i++) {
            int color = gauge.colorAt(inner > 1 ? i / (float) (inner - 1) : 0F);
            GuiDraw.fill(graphics, x + 1 + i, y + 1, x + 2 + i, y + HEIGHT - 1, color);
        }
        GuiDraw.text(graphics, Minecraft.getInstance().font, text, x + 4, y + 2, 0xFFFFFFFF, true);
    }
}
//?}
