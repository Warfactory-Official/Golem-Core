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
 * Publishes what a block holds as a read-only, sideless view on NeoForge's item capability, for viewers such as
 * hologram mods. Nothing can be inserted or extracted through it and every stack it hands out is a copy, so it
 * cannot move or duplicate items; automation keeps vanilla's sided rules. Fabric's transfer API already reads
 * {@code Container} block entities and {@code WorldlyContainerHolder} blocks, so this does nothing there or on Forge.
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
    *///?}
}
