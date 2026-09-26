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
 * <p>Calling {@code super} first in both directions is deliberate: vanilla writes its own keys there,
 * and skipping it silently drops them.
 */
public abstract class CompatBlockEntity extends BlockEntity {

    protected CompatBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    //? if forge {
    /*/^*
     * Forge's per-block-entity render culling box.
     *
     * <p>Forge culls each block entity against this AABB, and its default is the block's **collision
     * shape** — effectively one block for a machine. A model that spans several blocks therefore pops
     * out of view the moment its origin block leaves the frustum, which is the "culls too aggressively
     * at the screen edges" symptom.
     *
     * <p>NeoForge has the same hook but hangs it off the *renderer*, so there it is handled centrally in
     * {@code LegacyBERAdapter#getRenderBoundingBox}. Forge 1.20.1 puts it on the block entity instead,
     * so the same decision has to be made from here — asking the registered renderer the same question,
     * so both loaders apply one rule: a renderer that declares {@code shouldRenderOffScreen()} has
     * already said it draws outside its own block and is never culled; everything else keeps the
     * one-block box and culls normally. Fabric has no such hook and was never affected.
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
     * <p>Opt-in rather than default-on: vanilla's update tag holds only identity, and syncing a whole
     * block entity's NBT to every client on chunk load is a real bandwidth decision, not a formatting
     * one. Machines that drive their GUI from persisted fields turn it on; everything else keeps
     * vanilla's behaviour unchanged.
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

    /** Sends the update tag (see {@link #syncFullTagOnChunkLoad}) to clients tracking this block. Server only. */
    protected final void syncToClients() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return;
        var packet = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        var chunk = new net.minecraft.world.level.ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        for (var player : server.getChunkSource().chunkMap.getPlayers(chunk, false)) player.connection.send(packet);
    }

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
     * Drops keys that must not travel with the block when it is picked up as an item — typically an
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        saveData(new TagDataOut(tag, null));
    }
    *///?}

    //? if <26.1 {
    
    /*/^*
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
