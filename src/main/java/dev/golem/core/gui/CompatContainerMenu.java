package dev.golem.core.gui;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/** Bridges the container-click input enum rename while keeping feature menus version-neutral. */
public abstract class CompatContainerMenu extends AbstractContainerMenu {

    public enum ClickAction {
        PICKUP,
        QUICK_MOVE,
        SWAP,
        CLONE,
        THROW,
        QUICK_CRAFT,
        PICKUP_ALL
    }

    protected CompatContainerMenu(MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Override
    public final void clicked(int slotId, int button,
                              //? if >=26.1 {
                              net.minecraft.world.inventory.ContainerInput
                              //?} else {
                              /*net.minecraft.world.inventory.ClickType
                              *///?}
                              input, Player player) {
        ClickAction action = ClickAction.valueOf(input.name());
        if (!handleClick(slotId, button, action, player)) {
            super.clicked(slotId, button, input, player);
        }
    }

    /** Returns true when the feature consumed the click and vanilla handling must be skipped. */
    protected boolean handleClick(int slotId, int button, ClickAction action, Player player) {
        return false;
    }
}
