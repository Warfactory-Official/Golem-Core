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

    /^*
     * Side effects of this block entity being removed — dropping an inventory, most of the time.
     *
     * <p>26.1 declares this on {@code BlockEntity} and calls it itself, with a default that drops the
     * contents of any block entity which is a {@link net.minecraft.world.Container}. Below 26.1 there is
     * no such hook and no such default: dropping is each block's own business, done from
     * {@code Block.onRemove}. A block entity written against 26.1 therefore keeps compiling and silently
     * stops dropping anything, which voids a machine's inventory when it is broken.
     *
     * <p>This declares the method so overrides still bind, and reproduces 26.1's default exactly. It is
     * inert until something calls it — {@code dev.golem.core.world.Compat.preRemoveSideEffects} is the
     * bridge, invoked from the block's removal hook.
     ^/
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (this instanceof net.minecraft.world.Container container && this.level != null) {
            net.minecraft.world.Containers.dropContents(this.level, pos, container);
        }
    }
    *///?}
}
