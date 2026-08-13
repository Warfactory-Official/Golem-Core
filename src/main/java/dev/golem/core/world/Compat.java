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
}
