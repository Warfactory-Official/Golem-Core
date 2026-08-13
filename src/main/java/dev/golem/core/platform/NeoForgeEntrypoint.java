package dev.golem.core.platform;

//? if neoforge {
import dev.golem.core.GolemCore;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(GolemCore.MOD_ID)
public class NeoForgeEntrypoint {

    /**
     * Golem Core's own mod event bus. Kept because some of what this library emulates is delivered by
     * NeoForge as a mod-bus event rather than a callable registry — item colours, for one — and the
     * emulation has to be able to subscribe from shared code that knows nothing about buses.
     */
    private static IEventBus modBus;

    public NeoForgeEntrypoint(IEventBus modBus) {
        NeoForgeEntrypoint.modBus = modBus;
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
    }

    /** @throws IllegalStateException if called before the mod is constructed. */
    public static IEventBus modBus() {
        if (modBus == null) throw new IllegalStateException("Golem Core's mod bus is not available yet");
        return modBus;
    }
}
//?}
