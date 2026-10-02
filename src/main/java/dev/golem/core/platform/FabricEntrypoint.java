package dev.golem.core.platform;

//? if fabric {
/*import dev.golem.core.GolemCore;
import net.fabricmc.api.ModInitializer;

public class FabricEntrypoint implements ModInitializer {

    @Override
    public void onInitialize() {
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
        // The GUI scene renderer must be registered before the game renderer builds its PiP list.
        if (Platform.isClient()) dev.golem.core.gui.GuiScenes.bootstrap();
    }
}
*///?}
