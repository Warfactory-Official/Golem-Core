package dev.golem.core.client;

/** Packed colour helpers whose vanilla holder changed name after 1.21.1. */
public final class Colors {

    private Colors() {}

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        //? if >=26.1 {
        return net.minecraft.util.ARGB.colorFromFloat(alpha, red, green, blue);
        //?} elif >=1.21 {
        /*return net.minecraft.util.FastColor.ARGB32.colorFromFloat(alpha, red, green, blue);
        *///?} else {
        /*return net.minecraft.util.FastColor.ARGB32.color(
                Math.round(alpha * 255F), Math.round(red * 255F),
                Math.round(green * 255F), Math.round(blue * 255F));
        *///?}
    }

    public static int black(int alpha) {
        //? if >=26.1 {
        return net.minecraft.util.ARGB.color(alpha, 0, 0, 0);
        //?} else {
        /*return net.minecraft.util.FastColor.ARGB32.color(alpha, 0, 0, 0);
        *///?}
    }
}
