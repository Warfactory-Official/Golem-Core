package dev.golem.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** A client-side callback for changing the computed camera FOV. */
public final class Fov {

    private Fov() {}

    @FunctionalInterface
    public interface Modifier {
        float apply(ItemStack held, float fov, float partialTick);
    }

    private static Modifier modifier = (held, fov, partialTick) -> fov;
    private static boolean registered;

    /** Installs the one client FOV callback used by the mod. */
    public static void register(Modifier modifier) {
        if (registered) return;
        registered = true;
        Fov.modifier = modifier;

        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) ->
                        event.setFOV(apply((float) event.getFOV(), (float) event.getPartialTick())));
        //?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ViewportEvent.ComputeFov event) ->
                        event.setFOV(apply((float) event.getFOV(), (float) event.getPartialTick())));
        *///?}
    }

    /** Applies the installed callback to the local player's main-hand item. */
    public static float apply(float fov, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        return modifier.apply(held, fov, partialTick);
    }
}
