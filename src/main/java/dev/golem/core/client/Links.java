package dev.golem.core.client;

/**
 * Opens a web address in the desktop's browser, as Forge-era {@code Desktop.browse} did.
 *
 * <p>Vanilla's platform helper moved from {@code net.minecraft.Util} to {@code net.minecraft.util.Util}
 * at 26.1; the call is otherwise the same. Client only.
 */
public final class Links {

    private Links() {}

    public static void open(String url) {
        try {
            //? if >=1.21.11 {
            //? if >=26.3 {
            /*com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));
            *///?} else {
            net.minecraft.util.Util.getPlatform().openUri(url);
            //?}
            //?} else {
            /*net.minecraft.Util.getPlatform().openUri(url);
            *///?}
        } catch (Exception ignored) {
            // 1.7 swallowed every failure to reach a browser, and so does this.
        }
    }
}
