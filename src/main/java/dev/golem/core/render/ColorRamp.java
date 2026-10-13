package dev.golem.core.render;

import java.util.function.IntUnaryOperator;

/**
 * A per-channel linear remap from one colour band onto another.
 *
 * <p>Greyscale shape art is drawn once, inside a known luminance band, and recoloured per material by
 * mapping that band onto the material's own light and dark colours. Each channel is interpolated
 * independently along the straight line through the two ends. Values outside the band extrapolate along the
 * same line, then truncate into a byte; the art is authored to stay inside it.
 *
 * <p>A multiply cannot express this: a tint has no offset term, so it can reach the target's light end or its
 * dark end but never both.
 *
 * <p>No Minecraft type is named here, so the arithmetic can be tested in a plain JVM.
 *
 * @param sourceLight the light end of the band the art is drawn in, {@code 0xRRGGBB}
 * @param sourceDark  the dark end of that band
 * @param targetLight the colour {@code sourceLight} becomes
 * @param targetDark  the colour {@code sourceDark} becomes
 */
public record ColorRamp(int sourceLight, int sourceDark, int targetLight, int targetDark) {

    /** The band HBM's shape art is drawn in: white highlights down to {@code 0x505050} shadows. */
    public static final int DEFAULT_SOURCE_LIGHT = 0xFFFFFF;
    public static final int DEFAULT_SOURCE_DARK = 0x505050;

    public ColorRamp {
        sourceLight &= 0xFFFFFF;
        sourceDark &= 0xFFFFFF;
        targetLight &= 0xFFFFFF;
        targetDark &= 0xFFFFFF;
        for (int shift = 0; shift <= 16; shift += 8) {
            if (((sourceLight >> shift) & 0xFF) == ((sourceDark >> shift) & 0xFF)) {
                throw new IllegalArgumentException(String.format(
                        "source band %06X..%06X has no width in one channel; a zero-width band cannot be"
                                + " interpolated", sourceLight, sourceDark));
            }
        }
    }

    /** A ramp from the default shape-art band onto a material's own two solid colours. */
    public static ColorRamp of(int targetLight, int targetDark) {
        return new ColorRamp(DEFAULT_SOURCE_LIGHT, DEFAULT_SOURCE_DARK, targetLight, targetDark);
    }

    /** Remaps one {@code 0xAARRGGBB} pixel, alpha untouched. */
    public int applyArgb(int argb) {
        return (argb & 0xFF000000)
                | (channel(argb, 16) << 16)
                | (channel(argb, 8) << 8)
                | channel(argb, 0);
    }

    /**
     * The same remap over {@code 0xAABBGGRR} pixels, the layout {@code NativeImage} keeps in memory on
     * every version this library targets, and therefore the one a raw pixel copy hands out.
     */
    public IntUnaryOperator abgrOperator() {
        return abgr -> swapRedBlue(applyArgb(swapRedBlue(abgr)));
    }

    /** Exchanges the first and third colour bytes, leaving alpha and green where they are. */
    public static int swapRedBlue(int colour) {
        return (colour & 0xFF00FF00) | ((colour >> 16) & 0xFF) | ((colour & 0xFF) << 16);
    }

    private int channel(int argb, int shift) {
        double value = (argb >> shift) & 0xFF;
        double from = (sourceLight >> shift) & 0xFF;
        double to = (sourceDark >> shift) & 0xFF;
        double position = (value - from) / (to - from);
        double light = (targetLight >> shift) & 0xFF;
        double dark = (targetDark >> shift) & 0xFF;
        return (int) (light + position * (dark - light)) & 0xFF;
    }
}
