package com.wf.gemrender.iris;

import com.wf.gemrender.GemRender;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}

@EventBusSubscriber(modid = GemRender.MOD_ID, value = Dist.CLIENT)
public final class IrisPbrEvents {
    private IrisPbrEvents() {
    }

    //? if neoforge {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        IrisPbrBridge.install(ModList.get()
                .isLoaded("iris"));
    }
    //?} else {
    /*@SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        IrisPbrBridge.install(ModList.get()
                .isLoaded("oculus"));
    }
    *///?}
}
