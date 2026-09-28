package dev.golem.core.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Text in vanilla's other fonts. The standard galactic alphabet (the enchanting table's {@code minecraft:alt})
 * is named by a {@code FontDescription} on 26.1 and by a bare font id before it.
 */
public final class Fonts {

    private Fonts() {}

    /** {@code text} in the standard galactic alphabet (1.7 {@code Minecraft.standardGalacticFontRenderer}). */
    public static MutableComponent galactic(String text) {
        //? if >=26.1 {
        Style style = Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                dev.golem.core.Ids.of("minecraft", "alt")));
        //?} else {
        /*Style style = Style.EMPTY.withFont(dev.golem.core.Ids.of("minecraft", "alt"));
        *///?}
        return Component.literal(text).withStyle(style);
    }
}
