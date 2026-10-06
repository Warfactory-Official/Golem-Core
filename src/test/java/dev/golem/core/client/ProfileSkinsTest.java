package dev.golem.core.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Vanilla {@code processLegacySkin} on ARGB pixels. */
class ProfileSkinsTest {

    @Test
    void legacySkinGainsMirroredLimbsAndOpaqueBase() {
        int[] legacy = new int[64 * 32];
        // right leg front column 0 (x 4..7, y 20..31): distinct colours, semi-transparent
        for (int x = 4; x < 8; x++) legacy[20 * 64 + x] = 0x40000000 | x;
        // the whole legacy hat/arm block (x 32..63, y 0..31) fully opaque: the notch hack clears it
        for (int y = 0; y < 32; y++) for (int x = 32; x < 64; x++) legacy[y * 64 + x] = 0xFF123456;

        int[] skin = ProfileSkins.processLegacySkin(legacy, 64, 32);

        assertEquals(64 * 64, skin.length);
        // copyRect(4, 20, 16, 32, 4, 12, mirrorX): source x 4..7 lands mirrored at x 20..23, y 52 (then made opaque,
        // as the left leg is base layer)
        for (int x = 0; x < 4; x++) assertEquals(0xFF000000 | (7 - x), skin[52 * 64 + 20 + x]);
        assertEquals(0xFF000004, skin[20 * 64 + 4]);
        // the fully opaque legacy hat is made transparent
        assertEquals(0x00123456, skin[0 * 64 + 40]);
        // the second-layer rows below the copies stay clear
        assertEquals(0, skin[36 * 64 + 4]);
    }

    @Test
    void modernSkinKeepsItsHatLayer() {
        int[] modern = new int[64 * 64];
        for (int y = 0; y < 16; y++) for (int x = 32; x < 64; x++) modern[y * 64 + x] = 0xFF123456;
        int[] skin = ProfileSkins.processLegacySkin(modern, 64, 64);
        assertEquals(0xFF123456, skin[0 * 64 + 40]);
        assertEquals(0xFF000000, skin[0]); // base head made opaque
    }

    @Test
    void wrongSizeIsRejected() {
        assertThrows(IllegalStateException.class, () -> ProfileSkins.processLegacySkin(new int[32 * 32], 32, 32));
    }
}
