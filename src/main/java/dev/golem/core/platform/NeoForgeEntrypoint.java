package dev.golem.core.platform;

//? if neoforge {
import dev.golem.core.GolemCore;
import net.neoforged.fml.common.Mod;

@Mod(GolemCore.MOD_ID)
public class NeoForgeEntrypoint {

    public NeoForgeEntrypoint() {
        GolemCore.LOG.info("Golem Core loaded on {} for MC {}", GolemCore.loader(), GolemCore.MC_VERSION);
    }
}
//?}
