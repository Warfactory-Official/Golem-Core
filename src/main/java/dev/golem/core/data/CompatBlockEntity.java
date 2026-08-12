package dev.golem.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

//? if >=26.1 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
*///?}

/**
 * Block entity base that persists through {@link DataIn}/{@link DataOut} on every version.
 *
 * <p>Subclasses override {@link #loadData}/{@link #saveData} and never see the version difference.
 * Each era's vanilla hook is bridged below:
 *
 * <ul>
 *   <li><b>26.1</b> overrides {@code loadAdditional(ValueInput)} / {@code saveAdditional(ValueOutput)}
 *       and wraps the argument.</li>
 *   <li><b>Pre-26.1</b> overrides {@code loadAdditional(CompoundTag, HolderLookup.Provider)} /
 *       {@code saveAdditional(CompoundTag, HolderLookup.Provider)} and wraps the tag, passing the
 *       registry provider through so codec-based reads and writes resolve registry entries.</li>
 * </ul>
 *
 * <p>Calling {@code super} first in both directions is deliberate: vanilla writes its own keys there,
 * and skipping it silently drops them.
 */
public abstract class CompatBlockEntity extends BlockEntity {

    protected CompatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Reads this block entity's persisted state. */
    protected void loadData(DataIn in) {}

    /** Writes this block entity's persisted state. */
    protected void saveData(DataOut out) {}

    /**
     * Drops keys that must not travel with the block when it is picked up as an item — typically an
     * inventory that is already carried by a data component. Bridged from vanilla's
     * {@code removeComponentsFromTag}, which affects only the save-to-item path, not the world save.
     */
    protected void removeSavedComponents(DataOut out) {}

    //? if >=26.1 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadData(new ValueDataIn(input));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveData(new ValueDataOut(output));
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        removeSavedComponents(new ValueDataOut(output));
    }
    //?} else {
    /*@Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadData(new TagDataIn(tag, registries));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveData(new TagDataOut(tag, registries));
    }
    *///?}
}
