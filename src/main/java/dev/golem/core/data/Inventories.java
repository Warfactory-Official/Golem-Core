package dev.golem.core.data;

import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Item-list persistence in vanilla's {@code "Items"} format.
 *
 * <p>This is the escape hatch pattern for the whole {@code data} package. {@link DataIn}/{@link DataOut}
 * deliberately hide their backend, but vanilla helpers like {@link ContainerHelper} take the raw
 * {@code ValueOutput} or {@code CompoundTag} — so something has to bridge them. Rather than exposing the
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
        //? if >=26.1 {
        ContainerHelper.saveAllItems(unwrap(out), stacks, alsoWhenEmpty);
        //?} else {
        /*TagDataOut tagOut = (TagDataOut) out;
        ContainerHelper.saveAllItems(tagOut.tag(), stacks, alsoWhenEmpty, tagOut.registries());
        *///?}
    }

    /** Reads a stack list from {@code "Items"} into {@code stacks}. */
    public static void load(DataIn in, NonNullList<ItemStack> stacks) {
        //? if >=26.1 {
        ContainerHelper.loadAllItems(unwrap(in), stacks);
        //?} else {
        /*TagDataIn tagIn = (TagDataIn) in;
        ContainerHelper.loadAllItems(tagIn.tag(), stacks, tagIn.registries());
        *///?}
    }

    //? if >=26.1 {
    private static net.minecraft.world.level.storage.ValueOutput unwrap(DataOut out) {
        return ((ValueDataOut) out).delegate();
    }

    private static net.minecraft.world.level.storage.ValueInput unwrap(DataIn in) {
        return ((ValueDataIn) in).delegate();
    }
    //?}
}
