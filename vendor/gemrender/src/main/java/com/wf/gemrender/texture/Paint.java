package com.wf.gemrender.texture;

/**
 * A layer of {@link PaintArray}, as an instance wears it.
 *
 * @param layer         array layer; -1 = unpainted
 * @param tilesPerBlock pattern repeats per block of model space
 */
public record Paint(int layer, float tilesPerBlock) {
    public static final Paint NONE = new Paint(-1, 0.0f);

    public boolean isNone() {
        return layer < 0;
    }
}
