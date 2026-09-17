package dev.golem.core.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * The expected colours here are not derived from {@link ColorRamp}; they are read off HBM's shipped
 * per-material sprites, which were produced by the 1.7 mutator this reimplements. A change that breaks
 * the arithmetic breaks these rows.
 */
final class ColorRampTest {

    private static final int IRON_LIGHT = 0xFFFFFF;
    private static final int IRON_DARK = 0x353535;
    private static final int COPPER_LIGHT = 0xFDCA88;
    private static final int COPPER_DARK = 0x601E0D;
    private static final int WOOD_LIGHT = 0x896727;
    private static final int WOOD_DARK = 0x281E0B;

    private static int grey(int level) {
        return 0xFF000000 | (level << 16) | (level << 8) | level;
    }

    @Test
    void mapsTheBandEndsOntoTheMaterialsOwnColours() {
        ColorRamp iron = ColorRamp.of(IRON_LIGHT, IRON_DARK);
        assertEquals(0xFF000000 | IRON_LIGHT, iron.applyArgb(grey(0xFF)));
        assertEquals(0xFF000000 | IRON_DARK, iron.applyArgb(grey(0x50)));
    }

    @Test
    void reproducesTheShippedIronPlate() {
        ColorRamp iron = ColorRamp.of(IRON_LIGHT, IRON_DARK);
        assertEquals(0xFF3B3B3B, iron.applyArgb(grey(0x56)));
        assertEquals(0xFF4C4C4C, iron.applyArgb(grey(0x64)));
        assertEquals(0xFF5C5C5C, iron.applyArgb(grey(0x72)));
        assertEquals(0xFF797979, iron.applyArgb(grey(0x8B)));
        assertEquals(0xFFA8A8A8, iron.applyArgb(grey(0xB4)));
        assertEquals(0xFFC2C2C2, iron.applyArgb(grey(0xCB)));
    }

    @Test
    void reproducesTheShippedCopperWire() {
        ColorRamp copper = ColorRamp.of(COPPER_LIGHT, COPPER_DARK);
        assertEquals(0xFF601E0D, copper.applyArgb(grey(0x50)));
        assertEquals(0xFF652311, copper.applyArgb(grey(0x56)));
        assertEquals(0xFF7E3F24, copper.applyArgb(grey(0x72)));
        assertEquals(0xFF945736, copper.applyArgb(grey(0x8B)));
        assertEquals(0xFFB98053, copper.applyArgb(grey(0xB4)));
        assertEquals(0xFFCE9663, copper.applyArgb(grey(0xCB)));
        assertEquals(0xFFFDCA88, copper.applyArgb(grey(0xFF)));
    }

    @Test
    void reproducesTheShippedWoodGrip() {
        ColorRamp wood = ColorRamp.of(WOOD_LIGHT, WOOD_DARK);
        assertEquals(0xFF2B200B, wood.applyArgb(grey(0x56)));
        assertEquals(0xFF3A2C10, wood.applyArgb(grey(0x72)));
        assertEquals(0xFF483614, wood.applyArgb(grey(0x8B)));
        assertEquals(0xFF5F471B, wood.applyArgb(grey(0xB4)));
        assertEquals(0xFF6C511E, wood.applyArgb(grey(0xCB)));
        assertEquals(0xFF896727, wood.applyArgb(grey(0xFF)));
    }

    @Test
    void keepsAlphaAndRemapsEachChannelSeparately() {
        ColorRamp copper = ColorRamp.of(COPPER_LIGHT, COPPER_DARK);
        assertEquals(0x80000000 | COPPER_LIGHT, copper.applyArgb(0x80FFFFFF));
        assertEquals(0x00000000 | COPPER_DARK, copper.applyArgb(0x00505050));
        // A non-grey source pixel takes a different position on each channel's line.
        assertEquals(0xFFFDCA0D, copper.applyArgb(0xFFFFFF50));
    }

    @Test
    void appliesTheSameRemapToRawAbgrPixels() {
        ColorRamp copper = ColorRamp.of(COPPER_LIGHT, COPPER_DARK);
        int abgr = ColorRamp.swapRedBlue(grey(0x8B));
        assertEquals(ColorRamp.swapRedBlue(0xFF945736), copper.abgrOperator().applyAsInt(abgr));
        assertEquals(0xFF884522, ColorRamp.swapRedBlue(0xFF224588));
    }

    /**
     * The two pixel pipelines {@code NativeImage.mappedCopy} has, held against each other.
     *
     * <p>26.1 converts each pixel with {@code ARGB.fromABGR} before handing it to the operator and
     * {@code ARGB.toABGR} after; every version below hands over the raw int, which is ABGR in memory.
     * Both must end up writing the same bytes — feeding one version's operator to the other swaps red
     * and blue, which is a gold plate coming out blue and nothing in the build noticing.
     */
    @Test
    void bothMappedCopyPipelinesWriteTheSamePixel() {
        ColorRamp gold = ColorRamp.of(0xFFFF8B, 0xC26E00);
        int storedAbgr = ColorRamp.swapRedBlue(grey(0xFF));

        // 26.1: ARGB in, ARGB out, the caller converts on both sides.
        int modern = ColorRamp.swapRedBlue(gold.applyArgb(ColorRamp.swapRedBlue(storedAbgr)));
        // Below 26.1: the raw ABGR int goes straight through.
        int legacy = gold.abgrOperator().applyAsInt(storedAbgr);

        assertEquals(modern, legacy);
        assertEquals(ColorRamp.swapRedBlue(0xFFFFFF8B), modern);
    }

    @Test
    void refusesABandItCannotInterpolate() {
        assertThrows(IllegalArgumentException.class, () -> new ColorRamp(0x505050, 0x505050, 0xFFFFFF, 0x000000));
        assertThrows(IllegalArgumentException.class, () -> new ColorRamp(0xFF00FF, 0x5000FF, 0xFFFFFF, 0x000000));
    }
}
