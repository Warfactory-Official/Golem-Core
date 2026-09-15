package dev.golem.core.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Assorted vanilla operations whose spelling or shape changed between 1.21.1 and 26.1.
 *
 * <p>These are grouped rather than split into one class each because they share a property: the
 * difference is small, mechanical, and used from a handful of call sites. Anything that needs real
 * translation logic gets its own type (see {@code dev.golem.core.data}).
 *
 * <p>None of these are Stonecutter string replacements, because a replacement must be reversible 1:1
 * and none of these names are — {@code hurt} already exists on both eras with a different meaning,
 * and the fuel lookup is a different call graph entirely, not a rename.
 */
public final class Compat {

    private Compat() {}

    /**
     * Damages an entity. 26.1 split the server-authoritative path into {@code hurtServer}, which takes
     * the level explicitly; older versions have only {@code hurt}.
     */
    public static boolean hurt(Entity entity, ServerLevel level, DamageSource source, float amount) {
        //? if >=26.1 {
        return entity.hurtServer(level, source, amount);
        //?} else {
        /*return entity.hurt(source, amount);
        *///?}
    }

    /** Tests a block against the tool tier used by the shared HBM mining tools. */
    public static boolean isCorrectToolForDrops(
            net.minecraft.world.level.block.state.BlockState state, int tier) {
        //? if >=1.21.1 {
        if (tier >= 4) return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_NETHERITE_TOOL);
        if (tier == 3) return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL);
        if (tier == 2) return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL);
        if (tier == 1) return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_STONE_TOOL);
        return !state.is(net.minecraft.tags.BlockTags.INCORRECT_FOR_WOODEN_TOOL);
        //?} else {
        /*if (tier >= 4) return true;
        if (tier == 3) return !state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL);
        if (tier == 2) return !state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL);
        if (tier == 1) return !state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL);
        return !state.requiresCorrectToolForDrops();
        *///?}
    }

    /**
     * Runs an entity's fall-damage path for a fall of {@code fallDistance} blocks.
     *
     * <p>The first parameter widened from {@code float} to {@code double} at 26.1. Callers keep passing a
     * float; only this method knows the difference.
     */
    public static boolean causeFallDamage(Entity entity, float fallDistance, float damageMultiplier,
            DamageSource source) {
        //? if >=26.1 {
        return entity.causeFallDamage((double) fallDistance, damageMultiplier, source);
        //?} else {
        /*return entity.causeFallDamage(fallDistance, damageMultiplier, source);
        *///?}
    }

    /**
     * The hotbar slot a player currently has selected.
     *
     * <p>26.1 made {@code Inventory.selected} private behind {@code getSelectedSlot()}; below that the
     * field is public and there is no accessor.
     */
    public static int selectedSlot(net.minecraft.world.entity.player.Player player) {
        //? if >=26.1 {
        return player.getInventory().getSelectedSlot();
        //?} else {
        /*return player.getInventory().selected;
        *///?}
    }

    /** Sets the selected hotbar slot across the private-access change at 26.1. */
    public static void setSelectedSlot(net.minecraft.world.entity.player.Player player, int slot) {
        //? if >=26.1 {
        player.getInventory().setSelectedSlot(slot);
        //?} else {
        /*player.getInventory().selected = slot;
        *///?}
    }

    /** Tells the client about a server-side selected-slot change across the packet rename. */
    public static void syncSelectedSlot(net.minecraft.server.level.ServerPlayer player) {
        //? if >=26.1 {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(
                selectedSlot(player)));
        //?} else {
        /*player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(
                selectedSlot(player)));
        *///?}
    }

    /**
     * Adds the 3×9 inventory grid plus hotbar at the given origin, reproducing vanilla's layout
     * exactly (18px pitch, hotbar 58px below the grid's top row) so existing screen textures line up.
     *
     * <p>Takes a slot sink rather than the menu itself because {@code AbstractContainerMenu.addSlot}
     * is protected — only the menu subclass can hand it over, as {@code this::addSlot}. 26.1's own
     * {@code addStandardInventorySlots} is likewise protected, so the call site picks between them.
     */
    public static void addPlayerInventory(java.util.function.Consumer<Slot> addSlot, Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot.accept(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot.accept(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    /**
     * Furnace burn time for a stack, in ticks; 0 when the stack is not a fuel.
     *
     * <p>26.1 moved fuel data onto the level ({@code level.fuelValues()}); before 1.21.2 it was a
     * static map on {@code AbstractFurnaceBlockEntity}.
     */
    public static int burnDuration(Level level, ItemStack stack) {
        //? if >=26.1 {
        return level == null ? 0 : level.fuelValues().burnDuration(stack);
        //?} else {
        /*if (stack.isEmpty()) return 0;
        Integer time = net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity
                .getFuel().get(stack.getItem());
        return time == null ? 0 : time;
        *///?}
    }

    /**
     * Makes a block item's translation key follow its block ({@code block.<ns>.<name>}).
     *
     * <p>26.1 needs the opt-in because item description ids became explicit; before that
     * {@code BlockItem} already overrode {@code getDescriptionId} to defer to its block, so there is
     * nothing to call and nothing to do.
     */
    public static Item.Properties blockDescription(Item.Properties properties) {
        //? if >=26.1 {
        return properties.useBlockDescriptionPrefix();
        //?} else {
        /*return properties;
        *///?}
    }

    /**
     * Chunk coordinates. 26.1 made {@code ChunkPos} a record with {@code x()}/{@code z()} accessors;
     * before that they were public fields. Helpers rather than a replacement, because {@code .x()} is
     * far too common a spelling to rewrite blindly — {@code Vec3}, {@code Vector3f} and others have it.
     */
    public static int chunkX(net.minecraft.world.level.ChunkPos pos) {
        //? if >=26.1 {
        return pos.x();
        //?} else {
        /*return pos.x;
        *///?}
    }

    /** @see #chunkX */
    public static int chunkZ(net.minecraft.world.level.ChunkPos pos) {
        //? if >=26.1 {
        return pos.z();
        //?} else {
        /*return pos.z;
        *///?}
    }

    /**
     * Whether a block state renders as a full opaque cube.
     *
     * <p>26.1's {@code isSolidRender()} takes no arguments; before that it required a
     * {@code (BlockGetter, BlockPos)} pair. Several call sites — notably fallout's block predicates —
     * only have the state, with no level or position in scope, so the older branch approximates with
     * {@code canOcclude()}. That is the state's own occlusion flag rather than a position-aware
     * result: it agrees for every ordinary full block, and differs only for states whose occlusion
     * depends on neighbours. Documented approximation, not an exact port.
     */
    public static boolean isSolidRender(net.minecraft.world.level.block.state.BlockState state) {
        //? if >=26.1 {
        return state.isSolidRender();
        //?} else {
        /*return state.canOcclude();
        *///?}
    }

    /**
     * Whether a block state renders as a full opaque cube <em>at a position</em> — the exact form,
     * for the call sites that have a level and a position in scope (an explosion deciding whether the
     * block under a cleared space can hold fire, for instance).
     *
     * <p>Prefer this over {@link #isSolidRender(BlockState)}: that one has to approximate below 26.1
     * because it has nothing but the state.
     */
    public static boolean isSolidRender(net.minecraft.world.level.block.state.BlockState state,
                                        net.minecraft.world.level.BlockGetter level,
                                        net.minecraft.core.BlockPos pos) {
        //? if >=26.1 {
        return state.isSolidRender();
        //?} else {
        /*return state.isSolidRender(level, pos);
        *///?}
    }

    /**
     * Every item in a tag, as stacks. 26.1's {@code Registry.get(TagKey)} returns the holder set
     * directly; before that the accessor is called {@code getTag}. Same result either way, and the
     * empty case is an empty list rather than a throw on both.
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> itemsInTag(
            net.minecraft.tags.TagKey<Item> tag) {
        java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
        //? if >=26.1 {
        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(tag)
                .ifPresent(named -> named.forEach(h -> stacks.add(new net.minecraft.world.item.ItemStack(h))));
        //?} else {
        /*net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag)
                .ifPresent(named -> named.forEach(h -> stacks.add(new net.minecraft.world.item.ItemStack(h))));
        *///?}
        return stacks;
    }

    /**
     * Every item tag currently registered, as tag keys.
     *
     * <p>26.1's {@code getTags()} streams the {@code HolderSet.Named} itself (with {@code key()});
     * older versions stream a {@code Pair} of key and set. Callers that only want the keys — matching
     * tag paths against a naming convention, say — should not have to know which.
     */
    public static java.util.List<net.minecraft.tags.TagKey<Item>> itemTagKeys() {
        java.util.List<net.minecraft.tags.TagKey<Item>> keys = new java.util.ArrayList<>();
        //? if >=26.1 {
        net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().forEach(named -> keys.add(named.key()));
        //?} else {
        /*net.minecraft.core.registries.BuiltInRegistries.ITEM.getTags().forEach(pair -> keys.add(pair.getFirst()));
        *///?}
        return keys;
    }

    /**
     * Raytraces against blocks. Wraps the whole call rather than just the trailing argument because the
     * argument's <em>type</em> changed, not only its value: 26.1's {@code ClipContext} takes a
     * {@code CollisionContext}, older versions take a nullable {@code Entity}. A helper returning "the
     * right context" therefore cannot exist — only a helper that performs the clip.
     */
    public static net.minecraft.world.phys.BlockHitResult clip(Level level,
            net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to,
            net.minecraft.world.level.ClipContext.Block block,
            net.minecraft.world.level.ClipContext.Fluid fluid) {
        return level.clip(new net.minecraft.world.level.ClipContext(from, to, block, fluid,
                //? if >=1.20.5 {
                net.minecraft.world.phys.shapes.CollisionContext.empty()
                //?} else {
                /*(net.minecraft.world.entity.Entity) null
                *///?}
        ));
    }

    /**
     * The leftover stack for a fuel or crafting input — an empty bucket for lava, and so on.
     *
     * <p>26.1 models the remainder as an item instance that must be instantiated; older versions hand
     * back the {@code Item} itself. Returns {@link ItemStack#EMPTY} when there is no remainder.
     */
    public static ItemStack craftingRemainder(ItemStack stack) {
        // The accessor itself was renamed (getCraftingRemainder / getCraftingRemainingItem), so the
        // whole call sits inside the guard. Deliberately not leaning on a Stonecutter replacement:
        // those live in the consuming project's config, and this library has to compile on its own.
        //? if >=26.1 {
        net.minecraft.world.item.component.UseRemainder component =
                stack.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
        if (component != null) return component.convertInto().create();
        var remainder = stack.getItem().getCraftingRemainder();
        return remainder != null ? remainder.create() : ItemStack.EMPTY;
        //?} else {
        /*var remainder = stack.getItem().getCraftingRemainingItem();
        return remainder != null ? new ItemStack(remainder) : ItemStack.EMPTY;
        *///?}
    }

    /** Applies an item's crafting remainder property across the supported item-property APIs. */
    public static Item.Properties craftingRemainder(Item.Properties properties, Item remainder) {
        return properties.craftRemainder(remainder);
    }

    /** Counts non-empty stacks stored in an item's container component or legacy block-entity tag. */
    public static int containerItemCount(ItemStack stack, int slots) {
        //? if >=1.20.5 {
        net.minecraft.world.item.component.ItemContainerContents contents =
                stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        //? if >=26.1 {
        return contents == null ? 0 : (int) contents.nonEmptyItemCopyStream().count();
        //?} else {
        /*return contents == null ? 0 : (int) contents.nonEmptyStream().count();
        *///?}
        //?} else {
        /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("BlockEntityTag", net.minecraft.nbt.Tag.TAG_COMPOUND)) return 0;
        net.minecraft.core.NonNullList<ItemStack> items = net.minecraft.core.NonNullList.withSize(slots, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("BlockEntityTag"), items);
        int used = 0;
        for (ItemStack item : items) if (!item.isEmpty()) used++;
        return used;
        *///?}
    }

    /**
     * Runs a block entity's removal side effects — dropping its inventory, normally.
     *
     * <p>26.1 calls {@code BlockEntity.preRemoveSideEffects} itself whenever a block entity is removed,
     * and its default drops the contents of any {@code Container}. No such hook or default exists below
     * 26.1, where dropping is each block's own business. Call this from a block's removal override and
     * both eras behave the same: on 26.1 it does nothing, because vanilla has already done it.
     *
     * <p>Safe to call for blocks with no block entity, and for block entities that are not containers.
     */
    public static void preRemoveSideEffects(Level level, net.minecraft.core.BlockPos pos,
                                            net.minecraft.world.level.block.state.BlockState state) {
        // Nothing to do on 26.1: vanilla has already invoked it, and running it again would drop the
        // inventory twice.
        //? if <26.1 {
        /*if (level == null || level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof dev.golem.core.data.CompatBlockEntity be) {
            be.preRemoveSideEffects(pos, state);
        }
        *///?}
    }

    /**
     * Marks a chunk as needing to be written back to disk.
     *
     * <p>26.1 replaced the {@code setUnsaved(boolean)} setter with {@code markUnsaved()}; there is no
     * "mark saved" direction on 26.1, so only the one useful direction is exposed here. Callers that
     * write into a chunk's sections behind vanilla's back have to do this themselves — nothing else
     * will.
     */
    public static void markUnsaved(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        //? if >=26.1 {
        chunk.markUnsaved();
        //?} else {
        /*chunk.setUnsaved(true);
        *///?}
    }

    /**
     * Tells the chunk source that a section flipped between "has blocks" and "is all air".
     *
     * <p>26.1 added this call to {@code LevelChunk.setBlockState}; below it there is no such hook and
     * nothing to do. Only callers that write into a chunk's sections behind vanilla's back need it —
     * vanilla makes the call itself on the normal path.
     */
    public static void onSectionEmptinessChanged(net.minecraft.server.level.ServerLevel level,
                                                 int chunkX, int sectionY, int chunkZ, boolean empty) {
        //? if >=26.1 {
        level.getChunkSource().onSectionEmptinessChanged(chunkX, sectionY, chunkZ, empty);
        //?}
    }

    /**
     * Notifies the six blocks around {@code pos} that the block there changed.
     *
     * <p>26.1 added a redstone {@code Orientation} argument, used to give wire updates a direction;
     * null is the "no particular direction" case and is what vanilla itself passes from most call sites.
     */
    public static void updateNeighborsAt(Level level, net.minecraft.core.BlockPos pos,
                                         net.minecraft.world.level.block.Block block) {
        //? if >=26.1 {
        level.updateNeighborsAt(pos, block, null);
        //?} else {
        /*level.updateNeighborsAt(pos, block);
        *///?}
    }

    /**
     * Registers or clears a chunk's forced-loading ticket <b>without waiting for the chunk</b>.
     *
     * <p>{@code ServerLevel.setChunkForced} is this call followed by {@code getChunk(x, z)} — a
     * synchronous load that <b>generates</b> when nobody has the chunk. That trailing wait is the whole
     * difference, and it is not wanted by anything that forces chunks in bulk: a caller claiming a wide
     * area pays a full chunk generation, on the server thread, per chunk, before it does any work. The
     * ticket alone is a registration; the chunk system then produces the chunk on its own workers and
     * the caller picks it up with {@code getChunkNow} on a later tick.
     *
     * <p>The state change is identical either way — {@code setChunkForced} delegates the whole of it
     * here and only adds the wait.
     */
    public static void updateChunkForced(ServerLevel level, int chunkX, int chunkZ, boolean forced) {
        level.getChunkSource().updateChunkForced(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), forced);
    }

    /**
     * Runs the "this block was removed" hook on a cell's neighbours.
     *
     * <p>Vanilla fires it from inside {@code LevelChunk.setBlockState} when the update flags carry
     * {@code UPDATE_NEIGHBORS}. Callers that write into a chunk's sections behind vanilla's back skip it
     * and have to dispatch it themselves, or containers never drop and rails never re-link.
     *
     * <p>26.1 renamed it from {@code onRemove(state, level, pos, newState, moved)} to
     * {@code affectNeighborsAfterRemoval(level, pos, moved)} and dropped the new state, which it no
     * longer needs.
     */
    public static void affectNeighborsAfterRemoval(ServerLevel level, net.minecraft.core.BlockPos pos,
                                                   net.minecraft.world.level.block.state.BlockState oldState) {
        //? if >=26.1 {
        oldState.affectNeighborsAfterRemoval(level, pos, false);
        //?} else {
        /*oldState.onRemove(level, pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), false);
        *///?}
    }
}
