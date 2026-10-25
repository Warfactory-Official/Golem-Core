package dev.golem.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block's items on the loaders' item capability. Side-less, a read-only view for viewers such as hologram mods: it
 * cannot move or duplicate items. On a face, the hopper's rules for pipes and storage networks. Fabric's transfer API
 * already reads {@code Container} block entities and {@code WorldlyContainerHolder} blocks by itself.
 */
public final class ItemExposure {

    private ItemExposure() {}

    @FunctionalInterface
    public interface ContentsAt {
        @Nullable Container at(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity be);
    }

    /** Call once, while the mod bus is still accepting listeners. */
    public static void exposeContents(Predicate<Block> carriers, ContentsAt contents) {
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
                    Block[] blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(carriers)
                            .toArray(Block[]::new);
                    if (blocks.length == 0) return;
                    event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                            (level, pos, state, be, side) -> {
                                Container container = side == null ? contents.at(level, pos, state, be) : null;
                                return container == null ? null : new View(container);
                            }, blocks);
                });
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
                    Block[] blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(carriers)
                            .toArray(Block[]::new);
                    if (blocks.length == 0) return;
                    event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            (level, pos, state, be, side) -> {
                                Container container = side == null ? contents.at(level, pos, state, be) : null;
                                return container == null ? null : new View(container);
                            }, blocks);
                });
        *///?}
    }

    /**
     * Lets other mods' item automation (pipes, storage networks) reach a block on a face, by a hopper's rules:
     * {@code automation} returns the container a hopper would use there, and a {@code WorldlyContainer} limits slots and
     * moves per face through the loader's own wrapper. Away from the container's own block entity (a multiblock part)
     * a face lists every slot, so all views of the block agree: Holo Inventory adds up views that differ. Side-less
     * queries stay with {@link #exposeContents}. Fabric's transfer API does this by itself; 1.20.1 Forge reaches only
     * blocks with a block entity.
     */
    public static void exposeAutomation(Predicate<Block> carriers, ContentsAt automation) {
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
                    Block[] blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(carriers)
                            .toArray(Block[]::new);
                    if (blocks.length == 0) return;
                    event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                            (level, pos, state, be, side) -> {
                                Container container = side == null ? null : automation.at(level, pos, state, be);
                                if (!(container instanceof net.minecraft.world.WorldlyContainer worldly)) {
                                    return container == null ? null
                                            : net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(container);
                                }
                                var sided = new net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper(worldly, side);
                                return container == be ? sided : new FaceView(worldly, worldly.getSlotsForFace(side), sided);
                            }, blocks);
                });
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
                    Block[] blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(carriers)
                            .toArray(Block[]::new);
                    if (blocks.length == 0) return;
                    event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            (level, pos, state, be, side) -> {
                                Container container = side == null ? null : automation.at(level, pos, state, be);
                                if (!(container instanceof net.minecraft.world.WorldlyContainer worldly)) {
                                    return container == null ? null : new net.neoforged.neoforge.items.wrapper.InvWrapper(container);
                                }
                                var sided = new net.neoforged.neoforge.items.wrapper.SidedInvWrapper(worldly, side);
                                return container == be ? sided : new FaceView(worldly, worldly.getSlotsForFace(side), sided);
                            }, blocks);
                });
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class,
                (net.minecraftforge.event.AttachCapabilitiesEvent<BlockEntity> event) -> {
                    BlockEntity be = event.getObject();
                    if (!carriers.test(be.getBlockState().getBlock())) return;
                    event.addCapability(dev.golem.core.Ids.of("golemcore", "items"), new ForgeItems(be, automation));
                });
        *///?}
    }

    /**
     * Takes up to {@code max} items out through the item capability on {@code side}, as another mod's pipe would, and
     * returns them. Null where nothing answers. For tests.
     */
    public static @Nullable List<ItemStack> extract(Level level, BlockPos pos, Direction side, int max) {
        List<ItemStack> out = new ArrayList<>();
        //? if neoforge && >=26.1 {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return null;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            int left = max;
            for (int i = 0; i < handler.size() && left > 0; i++) {
                var resource = handler.getResource(i);
                if (resource.isEmpty()) continue;
                int got = handler.extract(i, resource, left, tx);
                if (got > 0) out.add(resource.toStack(got));
                left -= got;
            }
            tx.commit();
        }
        //?} elif neoforge {
        /*var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) return null;
        int left = max;
        for (int i = 0; i < handler.getSlots() && left > 0; i++) {
            ItemStack got = handler.extractItem(i, left, false);
            if (!got.isEmpty()) out.add(got);
            left -= got.getCount();
        }
        *///?} elif forge {
        /*BlockEntity be = level.getBlockEntity(pos);
        var handler = be == null ? null
                : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
        if (handler == null) return null;
        int left = max;
        for (int i = 0; i < handler.getSlots() && left > 0; i++) {
            ItemStack got = handler.extractItem(i, left, false);
            if (!got.isEmpty()) out.add(got);
            left -= got.getCount();
        }
        *///?} else {
        /*var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
        if (storage == null) return null;
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long left = max;
            for (var view : storage) {
                if (left <= 0) break;
                if (view.isResourceBlank()) continue;
                var resource = view.getResource();
                long got = view.extract(resource, left, tx);
                if (got > 0) out.add(resource.toStack((int) got));
                left -= got;
            }
            tx.commit();
        }
        *///?}
        return out;
    }

    /** Puts {@code stack} in through the item capability on {@code side}; how many went in, -1 where nothing answers. For tests. */
    public static int insert(Level level, BlockPos pos, Direction side, ItemStack stack) {
        //? if neoforge && >=26.1 {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return -1;
        int moved = 0;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            var resource = net.neoforged.neoforge.transfer.item.ItemResource.of(stack);
            for (int i = 0; i < handler.size() && moved < stack.getCount(); i++) {
                moved += handler.insert(i, resource, stack.getCount() - moved, tx);
            }
            tx.commit();
        }
        return moved;
        //?} elif neoforge {
        /*var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) return -1;
        return stack.getCount() - net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount();
        *///?} elif forge {
        /*BlockEntity be = level.getBlockEntity(pos);
        var handler = be == null ? null
                : be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
        if (handler == null) return -1;
        return stack.getCount() - net.minecraftforge.items.ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount();
        *///?} else {
        /*var storage = net.fabricmc.fabric.api.transfer.v1.item.ItemStorage.SIDED.find(level, pos, side);
        if (storage == null) return -1;
        try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
            long moved = storage.insert(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(stack), stack.getCount(), tx);
            tx.commit();
            return (int) moved;
        }
        *///?}
    }

    //? if forge {
    /*private record ForgeItems(BlockEntity be, ContentsAt automation)
            implements net.minecraftforge.common.capabilities.ICapabilityProvider {

        @Override
        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable Direction side) {
            if (capability != net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER || side == null
                    || be.getLevel() == null) {
                return net.minecraftforge.common.util.LazyOptional.empty();
            }
            Container container = automation.at(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
            if (container == null) return net.minecraftforge.common.util.LazyOptional.empty();
            net.minecraftforge.items.IItemHandler handler = container instanceof net.minecraft.world.WorldlyContainer worldly
                    ? new net.minecraftforge.items.wrapper.SidedInvWrapper(worldly, side)
                    : new net.minecraftforge.items.wrapper.InvWrapper(container);
            return net.minecraftforge.common.util.LazyOptional.of(() -> handler).cast();
        }
    }
    *///?}

    /** The non-empty stacks the NeoForge item capability at {@code pos} lists, in slot order. Null elsewhere or where nothing answers. */
    public static @Nullable List<ItemStack> read(Level level, BlockPos pos, @Nullable Direction side) {
        List<ItemStack> stacks = new ArrayList<>();
        //? if neoforge && >=26.1 {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return null;
        for (int i = 0; i < handler.size(); i++) {
            var resource = handler.getResource(i);
            if (!resource.isEmpty()) stacks.add(resource.toStack((int) handler.getAmountAsLong(i)));
        }
        return stacks;
        //?} elif neoforge {
        /*var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) stacks.add(stack.copy());
        }
        return stacks;
        *///?} else {
        /*return null;
        *///?}
    }

    /** What the item capability at {@code pos} shows, and how many items one insert and one extract per slot moved. */
    public record Probe(List<ItemStack> stacks, int moved) {}

    /**
     * Reads the item capability at {@code pos} as any mod would, then tries to move items through it; on 1.21.1 it
     * also grows every stack it was handed, which reaches the container only if that stack was not a copy.
     * Null off NeoForge or where nothing answers.
     */
    public static @Nullable Probe probe(Level level, BlockPos pos, @Nullable Direction side) {
        List<ItemStack> stacks = new ArrayList<>();
        int moved = 0;
        //? if neoforge && >=26.1 {
        var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, pos, side);
        if (handler == null) return null;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            for (int i = 0; i < handler.size(); i++) {
                var resource = handler.getResource(i);
                if (resource.isEmpty()) continue;
                stacks.add(resource.toStack((int) handler.getAmountAsLong(i)));
                moved += handler.extract(i, resource, 64, tx);
                moved += handler.insert(i, resource, 64, tx);
            }
            tx.commit();
        }
        return new Probe(stacks, moved);
        //?} elif neoforge {
        /*var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler == null) return null;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            stacks.add(stack.copy());
            stack.grow(1);
            moved += handler.extractItem(i, 64, false).getCount();
            ItemStack offer = stacks.get(stacks.size() - 1).copyWithCount(1);
            moved += offer.getCount() - handler.insertItem(i, offer, false).getCount();
        }
        return new Probe(stacks, moved);
        *///?} else {
        /*return null;
        *///?}
    }

    //? if neoforge && >=26.1 {
    private record View(Container container)
            implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {

        @Override public int size() { return container.getContainerSize(); }

        @Override
        public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index) {
            return net.neoforged.neoforge.transfer.item.ItemResource.of(container.getItem(index));
        }

        @Override public long getAmountAsLong(int index) { return container.getItem(index).getCount(); }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            return container.getMaxStackSize();
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            return false;
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                          net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                           net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }
    }

    /** Every slot of {@code container} for reading; moves only through {@code sided}, the face's own slots. */
    private record FaceView(net.minecraft.world.WorldlyContainer container, int[] faceSlots,
                            net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper sided)
            implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {

        private int face(int index) {
            for (int i = 0; i < faceSlots.length; i++) if (faceSlots[i] == index) return i;
            return -1;
        }

        @Override public int size() { return container.getContainerSize(); }

        @Override
        public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index) {
            return net.neoforged.neoforge.transfer.item.ItemResource.of(container.getItem(index));
        }

        @Override public long getAmountAsLong(int index) { return container.getItem(index).getCount(); }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            int face = face(index);
            return face < 0 ? container.getMaxStackSize() : sided.getCapacityAsLong(face, resource);
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            int face = face(index);
            return face >= 0 && sided.isValid(face, resource);
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                          net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            int face = face(index);
            return face < 0 ? 0 : sided.insert(face, resource, amount, transaction);
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                           net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            int face = face(index);
            return face < 0 ? 0 : sided.extract(face, resource, amount, transaction);
        }
    }
    //?}

    //? if neoforge && <26.1 {
    /*private record View(Container container) implements net.neoforged.neoforge.items.IItemHandler {

        @Override public int getSlots() { return container.getContainerSize(); }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return container.getItem(slot).copy();
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack,
                                                             boolean simulate) {
            return stack;
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override public int getSlotLimit(int slot) { return container.getMaxStackSize(); }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return false;
        }
    }

    private record FaceView(net.minecraft.world.WorldlyContainer container, int[] faceSlots,
                            net.neoforged.neoforge.items.wrapper.SidedInvWrapper sided)
            implements net.neoforged.neoforge.items.IItemHandler {

        private int face(int slot) {
            for (int i = 0; i < faceSlots.length; i++) if (faceSlots[i] == slot) return i;
            return -1;
        }

        @Override public int getSlots() { return container.getContainerSize(); }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            int face = face(slot);
            return face < 0 ? container.getItem(slot).copy() : sided.getStackInSlot(face);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack,
                                                             boolean simulate) {
            int face = face(slot);
            return face < 0 ? stack : sided.insertItem(face, stack, simulate);
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            int face = face(slot);
            return face < 0 ? net.minecraft.world.item.ItemStack.EMPTY : sided.extractItem(face, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            int face = face(slot);
            return face < 0 ? container.getMaxStackSize() : sided.getSlotLimit(face);
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            int face = face(slot);
            return face >= 0 && sided.isItemValid(face, stack);
        }
    }
    *///?}
}
