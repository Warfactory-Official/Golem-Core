package dev.golem.core.harness;

//? if neoforge {
import dev.golem.core.GolemCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

/** Starts the harness right after Golem Core is constructed. Found by annotation, so release jars hold no call to it. */
@EventBusSubscriber(modid = GolemCore.MOD_ID, value = Dist.CLIENT)
public final class HarnessSetup {

    private HarnessSetup() {}

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        Harness.init();
    }
}
//?} elif forge {
/*import dev.golem.core.GolemCore;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

/^* Starts the harness right after Golem Core is constructed. Found by annotation, so release jars hold no call to it. ^/
@Mod.EventBusSubscriber(modid = GolemCore.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HarnessSetup {

    private HarnessSetup() {}

    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        Harness.init();
    }
}
*///?}
