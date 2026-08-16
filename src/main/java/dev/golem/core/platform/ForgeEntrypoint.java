package dev.golem.core.platform;

//? if forge {
/*import dev.golem.core.GolemCore;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(GolemCore.MOD_ID)
public final class ForgeEntrypoint {

    private static IEventBus modBus;

    public ForgeEntrypoint() {
        modBus = FMLJavaModLoadingContext.get().getModEventBus();
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
    }

    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
*///?}
