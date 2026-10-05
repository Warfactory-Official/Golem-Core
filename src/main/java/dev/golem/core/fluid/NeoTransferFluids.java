package dev.golem.core.fluid;

//? if neoforge && >=26.1 {
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * 26.1 NeoForge: {@code Capabilities.Fluid.BLOCK}/{@code ITEM}, a {@code ResourceHandler<FluidResource>} in mB
 * whose insert and extract run inside a transaction the caller may roll back after the call returned. Block
 * moves are staged per owner ({@link FluidStaging}) and applied on the root commit; item moves exchange one
 * item through the caller's {@link ItemAccess}, which is transactional itself.
 */
final class NeoTransferFluids {

    private NeoTransferFluids() {}

    private static final Map<Object, WeakReference<Journal>> JOURNALS = new WeakHashMap<>();

    static void exposeBlocks(FluidPorts ports, Predicate<Block> carriers) {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Block[] blocks = BuiltInRegistries.BLOCK.stream().filter(carriers).toArray(Block[]::new);
            if (blocks.length == 0) return;
            event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, be, side) ->
                    level instanceof ServerLevel server ? BlockHandler.of(ports, server, pos, side) : null, blocks);
        });
    }

    static void exposeItems(FluidContainer contract, Predicate<Item> carriers) {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener((RegisterCapabilitiesEvent event) -> {
            Item[] items = BuiltInRegistries.ITEM.stream().filter(carriers).toArray(Item[]::new);
            if (items.length == 0) return;
            event.registerItem(Capabilities.Fluid.ITEM, (stack, access) ->
                    contract.handles(stack) ? new ItemHandler(contract, access) : null, items);
        });
    }

    // --- lookups (FluidCompat) ---

    static @Nullable FluidView find(Level level, BlockPos pos, @Nullable Direction side) {
        ResourceHandler<FluidResource> handler = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        return handler == null ? null : new View(handler, null);
    }

    static @Nullable FluidView findItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        // ItemAccess.forStack pins the item, so a container that changes item as it fills could not; a
        // one-slot container can hold whatever the exchange leaves.
        SimpleContainer container = new SimpleContainer(stack.copyWithCount(1));
        ItemAccess access = ItemAccess.forHandlerIndex(VanillaContainerWrapper.of(container), 0);
        ResourceHandler<FluidResource> handler = container.getItem(0).getCapability(Capabilities.Fluid.ITEM, access);
        return handler == null ? null : new View(handler, container);
    }

    private static final class View implements FluidView {

        private final ResourceHandler<FluidResource> handler;
        private final @Nullable SimpleContainer container;

        View(ResourceHandler<FluidResource> handler, @Nullable SimpleContainer container) {
            this.handler = handler;
            this.container = container;
        }

        @Override public Object handlerIdentity() { return handler; }
        @Override public int tanks() { return handler.size(); }
        @Override public Fluid fluid(int tank) { return handler.getResource(tank).isEmpty() ? Fluids.EMPTY : handler.getResource(tank).getFluid(); }
        @Override public long amount(int tank) { return handler.getAmountAsLong(tank); }
        @Override public long capacity(int tank) { return handler.getCapacityAsLong(tank, handler.getResource(tank)); }

        @Override
        public long insert(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            try (Transaction transaction = Transaction.openRoot()) {
                int moved = handler.insert(FluidResource.of(fluid), FluidUnits.saturate(mb), transaction);
                if (!simulate) transaction.commit();
                return moved;
            }
        }

        @Override
        public long extract(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            try (Transaction transaction = Transaction.openRoot()) {
                int moved = handler.extract(FluidResource.of(fluid), FluidUnits.saturate(mb), transaction);
                if (!simulate) transaction.commit();
                return moved;
            }
        }

        @Override
        public ItemStack container() {
            return container == null ? ItemStack.EMPTY : container.getItem(0);
        }
    }

    // --- blocks ---

    private static final class Journal extends SnapshotJournal<FluidStaging.Saved> {

        final FluidStaging staging;

        Journal(int tanks) {
            this.staging = new FluidStaging(tanks);
        }

        @Override protected FluidStaging.Saved createSnapshot() { return staging.save(); }
        @Override protected void revertToSnapshot(FluidStaging.Saved snapshot) { staging.restore(snapshot); }
        @Override protected void onRootCommit(FluidStaging.Saved originalState) { staging.commit(); }
    }

    private static final class BlockHandler implements ResourceHandler<FluidResource> {

        private final FluidPorts ports;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;
        private FluidMachine face;

        private BlockHandler(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side,
                             FluidMachine face) {
            this.ports = ports;
            this.level = level;
            this.pos = pos;
            this.side = side;
            this.face = face;
        }

        static @Nullable BlockHandler of(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side) {
            FluidMachine face = ports.at(level, pos, side);
            return face == null ? null : new BlockHandler(ports, level, pos.immutable(), side, face);
        }

        /** The live face; a capability cache may hold this handler past the machine it was made for. */
        private @Nullable FluidMachine face() {
            if (face.valid()) return face;
            FluidMachine fresh = ports.at(level, pos, side);
            if (fresh != null) face = fresh;
            return fresh;
        }

        private @Nullable Journal journal(FluidMachine face) {
            return FluidStaging.perOwner(JOURNALS, face, journal -> journal.staging.tanks(), Journal::new);
        }

        @Override
        public int size() {
            FluidMachine face = face();
            return face == null ? 0 : face.tanks();
        }

        @Override
        public FluidResource getResource(int index) {
            FluidMachine face = face();
            if (face == null) return FluidResource.EMPTY;
            Fluid fluid = journal(face).staging.fluid(face, index);
            return fluid == Fluids.EMPTY ? FluidResource.EMPTY : FluidResource.of(fluid);
        }

        @Override
        public long getAmountAsLong(int index) {
            FluidMachine face = face();
            return face == null ? 0L : journal(face).staging.amount(face, index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            FluidMachine face = face();
            return face == null ? 0L : journal(face).staging.capacity(face, index);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            FluidMachine face = face();
            return face != null && !resource.isEmpty() && face.valid() && index >= 0 && index < face.tanks()
                    && face.accepts(index, resource.getFluid());
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            FluidMachine face = face();
            if (face == null || resource.isEmpty() || amount <= 0) return 0;
            Journal journal = journal(face);
            journal.updateSnapshots(transaction);
            return (int) journal.staging.stageInsert(face, index, resource.getFluid(), amount);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            FluidMachine face = face();
            if (face == null || resource.isEmpty() || amount <= 0) return 0;
            Journal journal = journal(face);
            journal.updateSnapshots(transaction);
            return (int) journal.staging.stageExtract(face, index, resource.getFluid(), amount);
        }
    }

    // --- items ---

    /**
     * One container item through the caller's {@link ItemAccess}: a move replaces one item of the accessed
     * stack with what the contract says it becomes, inside the caller's transaction.
     */
    private static final class ItemHandler implements ResourceHandler<FluidResource> {

        private final FluidContainer contract;
        private final ItemAccess access;

        ItemHandler(FluidContainer contract, ItemAccess access) {
            this.contract = contract;
            this.access = access;
        }

        private ItemStack one() {
            ItemResource resource = access.getResource();
            return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(1);
        }

        @Override public int size() { return 1; }

        @Override
        public FluidResource getResource(int index) {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one)) return FluidResource.EMPTY;
            Fluid fluid = contract.fluid(one);
            return fluid == Fluids.EMPTY || contract.amount(one) <= 0L ? FluidResource.EMPTY : FluidResource.of(fluid);
        }

        @Override
        public long getAmountAsLong(int index) {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one) || contract.fluid(one) == Fluids.EMPTY) return 0L;
            return Math.max(0L, contract.amount(one));
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one)) return 0L;
            return contract.capacity(one, resource.isEmpty() ? Fluids.EMPTY : resource.getFluid());
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return !resource.isEmpty() && getCapacityAsLong(index, resource) > 0L;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            ItemStack one = one();
            if (one.isEmpty() || resource.isEmpty() || amount <= 0 || !contract.handles(one)) return 0;
            return move(contract.fill(one, resource.getFluid(), amount), transaction);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            ItemStack one = one();
            if (one.isEmpty() || resource.isEmpty() || amount <= 0 || !contract.handles(one)) return 0;
            return move(contract.drain(one, resource.getFluid(), amount), transaction);
        }

        private int move(FluidContainer.@Nullable Moved moved, TransactionContext transaction) {
            if (moved == null || moved.mb() <= 0L) return 0;
            ItemResource current = access.getResource();
            int swapped = moved.result().isEmpty()
                    ? access.extract(current, 1, transaction)
                    : access.exchange(ItemResource.of(moved.result()), 1, transaction);
            return swapped == 1 ? FluidUnits.saturate(moved.mb()) : 0;
        }
    }
}
//?}
