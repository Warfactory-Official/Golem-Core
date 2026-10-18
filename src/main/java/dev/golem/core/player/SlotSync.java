package dev.golem.core.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Keeps a player's slots honest when the server changes a stack every tick.
 *
 * <p>Vanilla never rewrites an inventory stack on its own, so its slot sync takes two shortcuts that are only
 * safe for stacks that hold still. A mod item that rewrites its own data every tick (a gun's animation clock, a
 * jetpack's fuel) always has a slot update in flight, and both shortcuts then go wrong:
 *
 * <ul>
 *   <li><b>Assumed remote copies.</b> After a drop (every version) and after a creative-mode slot write (26.1),
 *       the server records what it assumes the client now holds instead of sending it. An update sent a moment
 *       earlier lands after the client's own change and puts the old stack back on the client; the server, sure
 *       the client agrees, never corrects it. The client is left with a ghost item, which a creative client
 *       then makes real. {@link #forget} drops the assumption so the next broadcast sends the true stack.</li>
 *   <li><b>Creative echo (26.1).</b> With a creative screen open the client hands every slot the server pushes
 *       straight back as a creative slot write. By the time it arrives the server's stack has moved on, so the
 *       echo rolls the stack back a tick and, for an armour slot, replays the equip sound.
 *       {@link #echoSuppressed} marks the server push so the creative listener does not answer it.</li>
 * </ul>
 */
public final class SlotSync {

    /**
     * Client thread only: true while a server slot push is being applied, so the creative listener does not
     * send the pushed stack back to the server that just sent it.
     */
    public static boolean echoSuppressed;

    private SlotSync() {}

    /**
     * Forgets what the client is assumed to hold in {@code slot}: the remote copy is set to a stack that cannot
     * match, so the next {@code broadcastChanges} sends the slot's real contents.
     */
    public static void forget(AbstractContainerMenu menu, int slot) {
        if (slot < 0 || slot >= menu.slots.size()) return;
        ItemStack current = menu.getSlot(slot).getItem();
        menu.setRemoteSlot(slot, current.isEmpty() ? new ItemStack(Items.BARRIER) : ItemStack.EMPTY);
    }

    /** {@link #forget} for the slot of the player's open menu that shows the selected hotbar slot. */
    public static void forgetSelected(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        //? if >=26.1 {
        int selected = inventory.getSelectedSlot();
        //?} else {
        /*int selected = inventory.selected;
        *///?}
        AbstractContainerMenu menu = player.containerMenu;
        menu.findSlot(inventory, selected).ifPresent(slot -> forget(menu, slot));
    }
}
