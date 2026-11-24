package dev.golem.core.harness;

import net.minecraft.client.Minecraft;

/**
 * NeoForge holds a dev client on its loading screen when another mod only warns (REI's deprecated metadata, say). That
 * screen offers "Proceed to main menu" only when nothing failed, so pressing it never hides an error.
 */
final class HarnessLoadingWarnings {

    private HarnessLoadingWarnings() {}

    static void tick(Minecraft minecraft) {
        //? if neoforge {
        if (!(minecraft.screen instanceof net.neoforged.neoforge.client.gui.LoadingErrorScreen screen)) return;
        // FML labels its buttons with literal text, so the label is matched against FML's own translation.
        String proceed = net.neoforged.fml.i18n.FMLTranslations.parseMessage("fml.button.continue.launch");
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getString().equals(proceed)) {
                dev.golem.core.GolemCore.LOG.info("[harness] proceeding past mod loading warnings");
                //? if >=1.21.11 {
                button.onPress(new net.minecraft.client.input.KeyEvent(0, 0, 0));
                //?} else {
                /*button.onPress();
                *///?}
                return;
            }
        }
        //?}
    }
}
