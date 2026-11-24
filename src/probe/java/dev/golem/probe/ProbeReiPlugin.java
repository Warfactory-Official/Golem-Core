package dev.golem.probe;

//? if rei {
import dev.golem.core.rei.ReiClientPlugin;

/** The probe's REI client plugin: found by its annotation on NeoForge and by the {@code rei_client} entrypoint on Fabric. */
//? if neoforge {
@me.shedaniel.rei.forge.REIPluginClient
//?}
public final class ProbeReiPlugin extends ReiClientPlugin {

    public ProbeReiPlugin() {
        super(ProbeViewer.INSTANCE, subtypes -> {});
    }
}
//?}
