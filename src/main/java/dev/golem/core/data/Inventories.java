package dev.golem.core.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Item-list persistence in vanilla's {@code "Items"} format.
 *
 * <p>This is the escape hatch pattern for the whole {@code data} package. {@link DataIn}/{@link DataOut}
 * hide their backend, but vanilla helpers like {@link ContainerHelper} take the raw
 * {@code ValueOutput} or {@code CompoundTag}, so something has to bridge them. Rather than exposing the
 * backend on the interface (which would let version-specific types leak into every caller), the bridge
 * lives here, unwrapping the known implementation in one place.
 *
 * <p>The signatures differ by more than the argument type: pre-26.1 {@code ContainerHelper} also needs a
 * {@code HolderLookup.Provider} to resolve item components, which 26.1 carries inside {@code ValueOutput}.
 * That is exactly the kind of difference a caller should never have to think about.
 */
public final class Inventories {

    private Inventories() {}

    /** Writes a stack list under {@code "Items"}. {@code alsoWhenEmpty} forces the key to be written. */
    public static void save(DataOut out, NonNullList<ItemStack> stacks, boolean alsoWhenEmpty) {
        if (out instanceof MemoryData.Access access) {
            access.data().saveItems(stacks, alsoWhenEmpty);
            return;
        }
        //? if >=26.1 {
        ContainerHelper.saveAllItems(unwrap(out), stacks, alsoWhenEmpty);
        //?} elif >=1.20.5 {
        /*TagDataOut tagOut = (TagDataOut) out;
        ContainerHelper.saveAllItems(tagOut.tag(), stacks, alsoWhenEmpty, tagOut.registries());
        *///?} else {
        /*ContainerHelper.saveAllItems(((TagDataOut) out).tag(), stacks, alsoWhenEmpty);
        *///?}
    }

    /** Reads a stack list from {@code "Items"} into {@code stacks}. */
    public static void load(DataIn in, NonNullList<ItemStack> stacks) {
        if (in instanceof MemoryData.Access access) {
            access.data().loadItems(stacks);
            return;
        }
        //? if >=26.1 {
        ContainerHelper.loadAllItems(unwrap(in), stacks);
        //?} elif >=1.20.5 {
        /*TagDataIn tagIn = (TagDataIn) in;
        ContainerHelper.loadAllItems(tagIn.tag(), stacks, tagIn.registries());
        *///?} else {
        /*ContainerHelper.loadAllItems(((TagDataIn) in).tag(), stacks);
        *///?}
    }

    /** Returns the live 36-slot main inventory, excluding equipment and offhand slots. */
    public static NonNullList<ItemStack> main(Inventory inventory) {
        //? if >=26.1 {
        return inventory.getNonEquipmentItems();
        //?} else {
        /*return inventory.items;
        *///?}
    }

    /**
     * Copies every slot owned by a player, including armor and offhand, plus the selected hotbar slot.
     * This is an in-memory snapshot: player NBT is not a legal write surface for restoring
     * a live player and would also lose component values on some supported versions.
     */
    public static PlayerSnapshot snapshot(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) slots.add(inventory.getItem(slot).copy());
        return new PlayerSnapshot(List.copyOf(slots), dev.golem.core.world.Compat.selectedSlot(player));
    }

    /** Restores the exact copied slots and selected slot, returning true only after component-aware verification. */
    public static boolean restore(Player player, PlayerSnapshot snapshot) {
        Inventory inventory = player.getInventory();
        if (inventory.getContainerSize() != snapshot.slots().size()) return false;
        for (int slot = 0; slot < snapshot.slots().size(); slot++)
            inventory.setItem(slot, snapshot.slots().get(slot).copy());
        dev.golem.core.world.Compat.setSelectedSlot(player, snapshot.selectedSlot());
        inventory.setChanged();
        for (int slot = 0; slot < snapshot.slots().size(); slot++)
            if (!ItemStack.matches(snapshot.slots().get(slot), inventory.getItem(slot))) return false;
        return dev.golem.core.world.Compat.selectedSlot(player) == snapshot.selectedSlot();
    }

    public record PlayerSnapshot(List<ItemStack> slots, int selectedSlot) {}

    //? if >=26.1 {
    private static net.minecraft.world.level.storage.ValueOutput unwrap(DataOut out) {
        return ((ValueDataOut) out).delegate();
    }

    private static net.minecraft.world.level.storage.ValueInput unwrap(DataIn in) {
        return ((ValueDataIn) in).delegate();
    }
    //?}
}
