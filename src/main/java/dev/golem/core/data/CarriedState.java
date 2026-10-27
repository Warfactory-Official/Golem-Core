package dev.golem.core.data;

import dev.golem.core.world.ItemStacks;
//? if >=1.21.1 {
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BlockItemStateProperties;
//?}

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * One boolean of carried block data on an item ("damaged" machines, stardar links...), across the 1.21.1
 * component/NBT split: modern nodes keep it in the {@code BLOCK_STATE} component, older nodes in the
 * {@code BlockEntityTag}. The version branch lives here so content call sites stay version-neutral.
 */
public final class CarriedState {

    private CarriedState() {}

    /** Writes {@code property=true} into the item's carried block data when {@code value}; a false writes nothing. */
    public static void writeItemFlag(ItemStack stack, BooleanProperty property, String key, boolean value) {
        if (!value) return;
        //? if >=1.21.1 {
        var components = DataComponentMap.builder();
        components.set(DataComponents.BLOCK_STATE,
                BlockItemStateProperties.EMPTY.with(property, true));
        stack.applyComponents(components.build());
        //?} else {
        /*stack.getOrCreateTagElement("BlockEntityTag").putBoolean(key, true);
        *///?}
    }

    /** Whether the item's carried block data has {@code key=true}. */
    public static boolean readItemFlag(ItemStack stack, String key) {
        //? if >=1.21.1 {
        var properties = stack.get(DataComponents.BLOCK_STATE);
        return properties != null && "true".equals(properties.properties().get(key));
        //?} else {
        /*return ItemStacks.carriedBoolean(stack, key);
        *///?}
    }
}
