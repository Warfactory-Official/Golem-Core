package dev.golem.core.fluid;

//? if fabric {
/*import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

// Fabric (1.20.1, 1.21.1, 26.1): the Transfer API's FluidStorage.SIDED and FluidStorage.ITEM, a Storage of
// FluidVariant in droplets (81 per mB) inside transactions the caller may abort after the call returned.
// Block moves are staged per owner (FluidStaging) and applied on the outermost commit; item moves exchange
// one item through the caller's ContainerItemContext, which is transactional itself. Both are registered as
// lookup fallbacks, filtered by the carrier predicate, so nothing has to exist yet when they are registered.
final class FabricTransferFluids {

    private FabricTransferFluids() {}

    private static final Map<Object, WeakReference<Journal>> JOURNALS = new WeakHashMap<>();

    static void exposeBlocks(FluidPorts ports, Predicate<Block> carriers) {
        FluidStorage.SIDED.registerFallback((level, pos, state, be, side) -> {
            if (!(level instanceof ServerLevel server) || !carriers.test(state.getBlock())) return null;
            return BlockStorage.of(ports, server, pos, side);
        });
    }

    static void exposeItems(FluidContainer contract, Predicate<Item> carriers) {
        FluidStorage.ITEM.registerFallback((stack, context) ->
                carriers.test(stack.getItem()) && contract.handles(stack) ? new ItemStorage(contract, context) : null);
    }

    static @Nullable FluidView find(Level level, BlockPos pos, @Nullable Direction side) {
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, side);
        return storage == null ? null : new View(storage, null);
    }

    static @Nullable FluidView findItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ContainerItemContext context = slotContext(stack.copyWithCount(1));
        Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
        return storage == null ? null : new View(storage, context);
    }

    // withConstant would discard every exchange, so a private one-slot context that keeps what a move left.
    private static ContainerItemContext slotContext(ItemStack one) {
        net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage<ItemVariant> slot =
                new net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage<>() {
                    @Override
                    protected ItemVariant getBlankVariant() {
                        return ItemVariant.blank();
                    }

                    @Override
                    protected long getCapacity(ItemVariant variant) {
                        return Long.MAX_VALUE;
                    }
                };
        slot.variant = ItemVariant.of(one);
        slot.amount = one.getCount();
        return new ContainerItemContext() {
            @Override
            public net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<ItemVariant> getMainSlot() {
                return slot;
            }

            @Override
            public long insertOverflow(ItemVariant variant, long maxAmount, TransactionContext transaction) {
                return maxAmount;
            }

            @Override
            public List<net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage<ItemVariant>> getAdditionalSlots() {
                return List.of();
            }
        };
    }

    private static final class View implements FluidView {

        private final Storage<FluidVariant> storage;
        private final @Nullable ContainerItemContext context;

        View(Storage<FluidVariant> storage, @Nullable ContainerItemContext context) {
            this.storage = storage;
            this.context = context;
        }

        private List<StorageView<FluidVariant>> views() {
            List<StorageView<FluidVariant>> views = new ArrayList<>();
            for (StorageView<FluidVariant> view : storage) views.add(view);
            return views;
        }

        @Override public Object handlerIdentity() { return storage; }
        @Override public int tanks() { return views().size(); }

        @Override
        public Fluid fluid(int tank) {
            List<StorageView<FluidVariant>> views = views();
            if (tank < 0 || tank >= views.size() || views.get(tank).isResourceBlank()) return Fluids.EMPTY;
            return views.get(tank).getResource().getFluid();
        }

        @Override
        public long amount(int tank) {
            List<StorageView<FluidVariant>> views = views();
            return tank < 0 || tank >= views.size() ? 0L : FluidUnits.toMbFloor(views.get(tank).getAmount());
        }

        @Override
        public long capacity(int tank) {
            List<StorageView<FluidVariant>> views = views();
            return tank < 0 || tank >= views.size() ? 0L : FluidUnits.toMbFloor(views.get(tank).getCapacity());
        }

        @Override
        public long insert(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.insert(FluidVariant.of(fluid), FluidUnits.toDroplets(mb), transaction);
                if (!simulate) transaction.commit();
                return FluidUnits.toMbFloor(moved);
            }
        }

        @Override
        public long extract(Fluid fluid, long mb, boolean simulate) {
            if (fluid == Fluids.EMPTY || mb <= 0L) return 0L;
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.extract(FluidVariant.of(fluid), FluidUnits.toDroplets(mb), transaction);
                if (!simulate) transaction.commit();
                return FluidUnits.toMbFloor(moved);
            }
        }

        @Override
        public ItemStack container() {
            if (context == null || context.getItemVariant().isBlank()) return ItemStack.EMPTY;
            return context.getItemVariant().toStack((int) Math.min(Integer.MAX_VALUE, context.getAmount()));
        }
    }

    // --- blocks ---

    private static final class Journal extends SnapshotParticipant<FluidStaging.Saved> {

        final FluidStaging staging;

        Journal(int tanks) {
            this.staging = new FluidStaging(tanks);
        }

        @Override protected FluidStaging.Saved createSnapshot() { return staging.save(); }
        @Override protected void readSnapshot(FluidStaging.Saved snapshot) { staging.restore(snapshot); }
        @Override protected void onFinalCommit() { staging.commit(); }
    }

    private static Journal journal(FluidMachine face) {
        return FluidStaging.perOwner(JOURNALS, face, journal -> journal.staging.tanks(), Journal::new);
    }

    private static final class BlockStorage implements Storage<FluidVariant> {

        private final FluidPorts ports;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;
        private FluidMachine face;

        private BlockStorage(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side, FluidMachine face) {
            this.ports = ports;
            this.level = level;
            this.pos = pos;
            this.side = side;
            this.face = face;
        }

        static @Nullable BlockStorage of(FluidPorts ports, ServerLevel level, BlockPos pos, @Nullable Direction side) {
            FluidMachine face = ports.at(level, pos, side);
            return face == null ? null : new BlockStorage(ports, level, pos.immutable(), side, face);
        }

        @Nullable FluidMachine face() {
            if (face.valid()) return face;
            FluidMachine fresh = ports.at(level, pos, side);
            if (fresh != null) face = fresh;
            return fresh;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            FluidMachine face = face();
            long mb = FluidUnits.toMbFloor(maxAmount);
            if (face == null || resource.isBlank() || mb <= 0L) return 0L;
            Journal journal = journal(face);
            journal.updateSnapshots(transaction);
            return FluidUnits.toDroplets(journal.staging.stageInsertAny(face, resource.getFluid(), mb));
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            FluidMachine face = face();
            long mb = FluidUnits.toMbFloor(maxAmount);
            if (face == null || resource.isBlank() || mb <= 0L) return 0L;
            Journal journal = journal(face);
            journal.updateSnapshots(transaction);
            return FluidUnits.toDroplets(journal.staging.stageExtractAny(face, resource.getFluid(), mb));
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            FluidMachine face = face();
            List<StorageView<FluidVariant>> views = new ArrayList<>();
            if (face != null) for (int i = 0; i < face.tanks(); i++) views.add(new TankView(this, i));
            return views.iterator();
        }
    }

    // One tank, addressed by index; a foreign peer may extract through it directly.
    private record TankView(BlockStorage owner, int index) implements StorageView<FluidVariant> {

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            FluidMachine face = owner.face();
            long mb = FluidUnits.toMbFloor(maxAmount);
            if (face == null || resource.isBlank() || mb <= 0L) return 0L;
            Journal journal = journal(face);
            journal.updateSnapshots(transaction);
            return FluidUnits.toDroplets(journal.staging.stageExtract(face, index, resource.getFluid(), mb));
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public FluidVariant getResource() {
            FluidMachine face = owner.face();
            if (face == null) return FluidVariant.blank();
            Fluid fluid = journal(face).staging.fluid(face, index);
            return fluid == Fluids.EMPTY ? FluidVariant.blank() : FluidVariant.of(fluid);
        }

        @Override
        public long getAmount() {
            FluidMachine face = owner.face();
            return face == null ? 0L : FluidUnits.toDroplets(journal(face).staging.amount(face, index));
        }

        @Override
        public long getCapacity() {
            FluidMachine face = owner.face();
            return face == null ? 0L : FluidUnits.toDroplets(journal(face).staging.capacity(face, index));
        }
    }

    // --- items ---

    private static final class ItemStorage implements Storage<FluidVariant>, StorageView<FluidVariant> {

        private final FluidContainer contract;
        private final ContainerItemContext context;

        ItemStorage(FluidContainer contract, ContainerItemContext context) {
            this.contract = contract;
            this.context = context;
        }

        private ItemStack one() {
            ItemVariant variant = context.getItemVariant();
            return variant.isBlank() ? ItemStack.EMPTY : variant.toStack(1);
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            ItemStack one = one();
            long mb = FluidUnits.toMbFloor(maxAmount);
            if (one.isEmpty() || resource.isBlank() || mb <= 0L || !contract.handles(one)) return 0L;
            return move(contract.fill(one, resource.getFluid(), mb), transaction);
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            ItemStack one = one();
            long mb = FluidUnits.toMbFloor(maxAmount);
            if (one.isEmpty() || resource.isBlank() || mb <= 0L || !contract.handles(one)) return 0L;
            return move(contract.drain(one, resource.getFluid(), mb), transaction);
        }

        private long move(FluidContainer.@Nullable Moved moved, TransactionContext transaction) {
            if (moved == null || moved.mb() <= 0L) return 0L;
            ItemVariant current = context.getItemVariant();
            long swapped = moved.result().isEmpty()
                    ? context.extract(current, 1, transaction)
                    : context.exchange(ItemVariant.of(moved.result()), 1, transaction);
            return swapped == 1 ? FluidUnits.toDroplets(moved.mb()) : 0L;
        }

        @Override
        public Iterator<StorageView<FluidVariant>> iterator() {
            return List.<StorageView<FluidVariant>>of(this).iterator();
        }

        @Override
        public boolean isResourceBlank() {
            return getResource().isBlank();
        }

        @Override
        public FluidVariant getResource() {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one) || contract.amount(one) <= 0L) return FluidVariant.blank();
            Fluid fluid = contract.fluid(one);
            return fluid == Fluids.EMPTY ? FluidVariant.blank() : FluidVariant.of(fluid);
        }

        @Override
        public long getAmount() {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one) || contract.fluid(one) == Fluids.EMPTY) return 0L;
            return FluidUnits.toDroplets(contract.amount(one));
        }

        @Override
        public long getCapacity() {
            ItemStack one = one();
            if (one.isEmpty() || !contract.handles(one)) return 0L;
            return FluidUnits.toDroplets(contract.capacity(one, Fluids.EMPTY));
        }
    }
}
*///?}
