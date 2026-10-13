package dev.golem.core.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
 * <p>Call {@code super} first in both directions: vanilla writes its own keys there.
 */
public abstract class CompatBlockEntity extends BlockEntity {

    protected CompatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    //? if forge {
    /*/^*
     * Forge's per-block-entity render culling box.
     *
     * <p>Forge culls each block entity against this AABB, defaulting to the block's collision shape, which is
     * one block for a machine. A model spanning several blocks would vanish as soon as its origin block
     * leaves the frustum.
     *
     * <p>NeoForge has the same hook on the renderer, handled in {@code LegacyBERAdapter#getRenderBoundingBox};
     * Forge 1.20.1 puts it on the block entity. Both apply one rule: a renderer that declares
     * {@code shouldRenderOffScreen()} is never culled; everything else keeps the one-block box. Fabric has no
     * such hook.
     ^/
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return dev.golem.core.render.ClientRenderBounds.rendersOffScreen(this)
                ? net.minecraftforge.common.extensions.IForgeBlockEntity.INFINITE_EXTENT_AABB
                : new net.minecraft.world.phys.AABB(getBlockPos());
    }
    *///?}

    /**
     * Whether the chunk-load sync tag should carry every persisted field.
     *
     * <p>Opt-in: vanilla's update tag holds only identity, and syncing a whole block entity's NBT to every
     * client on chunk load costs bandwidth. Machines that drive their GUI from persisted fields turn it on.
     */
    protected boolean syncFullTagOnChunkLoad() {
        return false;
    }

    //? if >=1.21.1 {
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return syncFullTagOnChunkLoad() ? saveCustomOnly(registries) : super.getUpdateTag(registries);
    }
    //?} else {
    /*@Override
    public net.minecraft.nbt.CompoundTag getUpdateTag() {
        return syncFullTagOnChunkLoad() ? saveWithoutMetadata() : super.getUpdateTag();
    }
    *///?}

    /** Reads this block entity's persisted state. */
    protected void loadData(DataIn in) {}

    /** Writes this block entity's persisted state. */
    protected void saveData(DataOut out) {}

    /** Exercises the same persistence hooks against an in-memory representation, primarily for GameTests. */
    public final void copyPersistentDataTo(CompatBlockEntity target) {
        MemoryData data = new MemoryData();
        saveData(data.writer());
        target.loadData(data.reader());
    }

    /**
     * Drops keys that must not travel with the block when it is picked up as an item, typically an
     * inventory that is already carried by a data component. Bridged from vanilla's
     * {@code removeComponentsFromTag}, which affects only the save-to-item path, not the world save.
     */
    protected void removeSavedComponents(DataOut out) {}

    /** Inventory carried by this block entity's item form, or null when none is carried. */
    protected NonNullList<ItemStack> carriedItems() {
        return null;
    }

    /** Additional state carried in the item form's custom-data component, or null when none is needed. */
    protected CompoundTag collectCarriedData() {
        return null;
    }

    /** Applies additional state from the item form's custom-data component. */
    protected void applyCarriedData(CompoundTag data) {}

    /** Custom display name carried by this block entity's item form, or null when unnamed. */
    protected Component collectCarriedName() {
        return null;
    }

    /** Applies a custom display name from the item form. */
    protected void applyCarriedName(Component name) {}

    /**
     * Writes this block entity into an item stack across the save-to-item signature change.
     * Call this from drop handling instead of vanilla's version-specific method.
     */
    //? if >=26.1 {
    public final void saveToItemCompat(ItemStack stack) {
        stack.applyComponents(collectComponents());
    }
    //?}
    //? if >=1.21.1 && <26.1 {
    /*public final void saveToItemCompat(ItemStack stack) {
        saveToItem(stack, level.registryAccess());
    }
    *///?}
    //? if <1.21.1 {
    /*public final void saveToItemCompat(ItemStack stack) {
        saveToItem(stack);
    }
    *///?}

    //? if >=26.1 {
    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        NonNullList<ItemStack> items = carriedItems();
        if (items != null) {
            components.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyInto(items);
        }
        net.minecraft.world.item.component.CustomData data =
                components.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null) applyCarriedData(data.copyTag());
        Component name = components.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) applyCarriedName(name);
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        NonNullList<ItemStack> items = carriedItems();
        if (items != null) {
            components.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        }
        CompoundTag data = collectCarriedData();
        if (data != null) {
            components.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(data));
        }
        Component name = collectCarriedName();
        if (name != null) components.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
    }

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
    //?} elif >=1.20.5 {
    /*@Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        NonNullList<ItemStack> items = carriedItems();
        if (items != null) {
            components.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyInto(items);
        }
        net.minecraft.world.item.component.CustomData data =
                components.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data != null) applyCarriedData(data.copyTag());
        Component name = components.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) applyCarriedName(name);
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        NonNullList<ItemStack> items = carriedItems();
        if (items != null) {
            components.set(net.minecraft.core.component.DataComponents.CONTAINER,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
        }
        CompoundTag data = collectCarriedData();
        if (data != null) {
            components.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(data));
        }
        Component name = collectCarriedName();
        if (name != null) components.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadData(new TagDataIn(tag, registries));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveData(new TagDataOut(tag, registries));
    }
    *///?} else {
    /*@Override
    public void load(CompoundTag tag) {
        super.load(tag);
        loadData(new TagDataIn(tag, null));
        // the item form's carried data (1.20.5+: the custom_data component), arriving through BlockEntityTag
        if (tag.contains(LEGACY_CARRIED_KEY, 10)) applyCarriedData(tag.getCompound(LEGACY_CARRIED_KEY));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveData(new TagDataOut(tag, null));
        // Before 1.20.5 there are no implicit item components: a loot table can only copy_nbt from this tag. The
        // legacy loot conversion (HBM legacy-recipes.gradle.kts) turns copy_components of custom_data into a copy
        // of this key to BlockEntityTag, which BlockItem merges back into load() above. It describes the state
        // the rest of the tag already holds, so re-applying it on an ordinary chunk load changes nothing.
        CompoundTag carried = collectCarriedData();
        if (carried != null) tag.put(LEGACY_CARRIED_KEY, carried);
    }

    /^* NBT key of {@link #collectCarriedData} below 1.20.5, in the saved tag and in the item's BlockEntityTag. ^/
    public static final String LEGACY_CARRIED_KEY = "CarriedData";
    *///?}

    //? if <26.1 {
    
    /*/^*
     * Side effects of this block entity being removed, dropping an inventory, most of the time.
     *
     * <p>26.1 declares this on {@code BlockEntity} and calls it itself; the default drops the contents of a
     * {@link net.minecraft.world.Container}. Below 26.1 dropping is each block's own business, done from
     * {@code Block.onRemove}, so a block entity written against 26.1 would compile and silently void its
     * inventory when broken.
     *
     * <p>This declares the method so overrides bind, with 26.1's default. It does nothing until
     * {@code dev.golem.core.world.Compat.preRemoveSideEffects} calls it from the block's removal hook.
     ^/
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (this instanceof net.minecraft.world.Container container && this.level != null) {
            net.minecraft.world.Containers.dropContents(this.level, pos, container);
        }
    }
    *///?}
}
