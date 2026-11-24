package dev.golem.probe.platform.neoforge;

//? if neoforge {
import dev.golem.core.gametest.GameTests;
import dev.golem.core.platform.ModEntries;
import dev.golem.core.platform.Platform;
import dev.golem.probe.ProbeClient;
import dev.golem.probe.ProbeEntry;
import dev.golem.probe.ProbeGameTests;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

@Mod(ProbeEntry.MOD_ID)
public final class ProbeNeoForge {

    public ProbeNeoForge(IEventBus bus) {
        ModEntries.start(new ProbeEntry());
        if (Platform.isClient()) ProbeClient.init();
        bus.addListener((RegisterGameTestsEvent event) -> GameTests.register(event, ProbeGameTests.TESTS));
    }
}
//?}
