package dev.golem.core.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;

/** Sends a menu click through the current era's client container packet API. */
public final class ContainerClicks {

    public enum Action {
        PICKUP,
        QUICK_MOVE,
        SWAP,
        CLONE,
        THROW,
        QUICK_CRAFT,
        PICKUP_ALL
    }

    private ContainerClicks() {}

    public static void send(MultiPlayerGameMode gameMode, int containerId, int slotId, int button,
                            Action action, Player player) {
        //? if >=26.1 {
        gameMode.handleContainerInput(containerId, slotId, button,
                net.minecraft.world.inventory.ContainerInput.valueOf(action.name()), player);
        //?} else {
        /*gameMode.handleInventoryMouseClick(containerId, slotId, button,
                net.minecraft.world.inventory.ClickType.valueOf(action.name()), player);
        *///?}
    }
}
