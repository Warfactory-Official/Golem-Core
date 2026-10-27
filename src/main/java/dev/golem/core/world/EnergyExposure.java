package dev.golem.core.world;

//? if forge {
/*import net.minecraftforge.common.util.LazyOptional;
*///?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
//?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
//?}

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Publishes an {@link EnergySource} to whatever foreign energy system this node has, so other mods'
 * machines can read and drive one of ours.
 *
 * <p>The three systems want different things. 26.1 NeoForge takes a
 * {@code transfer.energy.EnergyHandler} whose insert and extract run inside a transaction the caller may
 * roll back after the call returned, so the handler must be able to put back what it changed; that is why
 * {@link EnergySource} carries a snapshot pair. 1.21.1 NeoForge and 1.20.1 Forge take an
 * {@code IEnergyStorage}, whose simulate flag asks the same question without changing anything, so the
 * snapshot pair is unused there.
 *
 * <p>Fabric ships no standard energy capability; there {@link #expose} registers with Team Reborn Energy
 * when that optional mod is loaded ({@link RebornEnergy}) and does nothing otherwise.
 *
 * <p>Call this once, while the mod bus is still accepting listeners: the loaders that have a
 * registration event subscribe to it here.
 */
public final class EnergyExposure {

    private EnergyExposure() {}

    /** Whether anything on this node can see what {@link #expose} publishes. */
    public static boolean supported() {
        //? if forgelike {
        return true;
        //?} else {
        /*return RebornEnergy.present(); // Fabric: only with the optional Team Reborn Energy
        *///?}
    }

    /**
     * @param blocks the blocks that carry the source, resolved lazily because registration runs before
     *               a consumer's own registries are necessarily populated.
     */
    public static void expose(EnergySource source, Supplier<Block[]> blocks) {
        //? if neoforge && >=26.1 {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK,
                                (level, pos, state, be, side) -> {
                                    if (!(level instanceof ServerLevel server)) return null;
                                    var face = source.forFace(server, pos, side);
                                    return face == null ? null : new Journalled(face, server, pos.immutable(), side);
                                },
                                blocks.get()));
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                (level, pos, state, be, side) -> {
                                    if (!(level instanceof ServerLevel server)) return null;
                                    var face = source.forFace(server, pos, side);
                                    return face == null ? null : new Simulated(face, server, pos.immutable(), side);
                                },
                                blocks.get()));
        *///?} elif forge {
        /*Block[][] carriers = new Block[1][];
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(
                net.minecraft.world.level.block.entity.BlockEntity.class,
                (net.minecraftforge.event.AttachCapabilitiesEvent<
                        net.minecraft.world.level.block.entity.BlockEntity> event) -> {
                    // Attach time is block-entity CONSTRUCTION: there is no level yet, so the block is
                    // all there is to match on and the level is resolved when the capability is asked for.
                    if (carriers[0] == null) carriers[0] = blocks.get();
                    net.minecraft.world.level.block.entity.BlockEntity be = event.getObject();
                    Block block = be.getBlockState().getBlock();
                    for (Block candidate : carriers[0]) {
                        if (candidate != block) continue;
                        event.addCapability(dev.golem.core.Ids.of("golemcore", "energy"),
                                new ForgeProvider(source, be));
                        return;
                    }
                });
        *///?} elif fabric {
        /*if (RebornEnergy.present()) RebornEnergy.expose(source, blocks);
        *///?}
    }

    //? if neoforge && >=26.1 {
    /**
     * A handler whose every mutation is journalled, because 26.1 lets a caller open a transaction,
     * take energy, and then abort, at which point what we handed over has to come back.
     */
    private static final class Journalled implements EnergyHandler {
        private static final EnergyJournalCache<Journal> JOURNALS = new EnergyJournalCache<>();
        private final Journal journal;

        private final EnergySource source;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;

        Journalled(EnergySource source, ServerLevel level, BlockPos pos,
                   @Nullable Direction side) {
            this.source = source;
            this.level = level;
            this.pos = pos;
            this.side = side;
            this.journal = JOURNALS.get(source, () -> new Journal(source, level, pos));
        }

        private Journal journal(EnergySource owner) {
            return owner == source ? journal : JOURNALS.get(owner, () -> new Journal(owner, level, pos));
        }

        private static final class Journal extends SnapshotJournal<Object> {
            private final EnergySource source;
            private final ServerLevel level;
            private final BlockPos pos;
            Journal(EnergySource source, ServerLevel level, BlockPos pos) {
                this.source = source; this.level = level; this.pos = pos;
            }
            @Override protected Object createSnapshot() { return source.snapshot(level, pos); }
            @Override protected void revertToSnapshot(Object state) { source.restore(level, pos, state); }
            @Override protected void releaseSnapshot(Object state) { source.releaseSnapshot(level, pos, state); }
            @Override protected void onRootCommit(Object originalState) { source.commit(level, pos); }
        }

        @Override public long getAmountAsLong() { return source.amountFe(level, pos); }
        @Override public long getCapacityAsLong() { return source.capacityFe(level, pos); }

        @Override
        public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0) return 0;
            var owner = source.forOperation(level, pos);
            if (owner == null) return 0;
            journal(owner).updateSnapshots(transaction);
            return (int) owner.insertFe(level, pos, side == null ? null : pos.relative(side), amount);
        }

        @Override
        public int extract(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0) return 0;
            var owner = source.forOperation(level, pos);
            if (owner == null) return 0;
            journal(owner).updateSnapshots(transaction);
            return (int) owner.extractFe(level, pos, amount);
        }
    }
    //?}

    //? if neoforge && <26.1 {
    /*private static final class Simulated implements net.neoforged.neoforge.energy.IEnergyStorage {

        private final EnergySource source;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;

        Simulated(EnergySource source, ServerLevel level, BlockPos pos,
                  @Nullable Direction side) {
            this.source = source;
            this.level = level;
            this.pos = pos;
            this.side = side;
        }

        @Override public int getEnergyStored() { return saturate(source.amountFe(level, pos)); }
        @Override public int getMaxEnergyStored() { return saturate(source.capacityFe(level, pos)); }
        @Override public boolean canReceive() { return source.insertableFe(level, pos) > 0; }
        @Override public boolean canExtract() { return source.extractableFe(level, pos) > 0; }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            if (simulate) return saturate(source.insertableFe(level, pos, maxReceive));
            var moved = source.insertFe(level, pos, side == null ? null : pos.relative(side), maxReceive);
            source.commit(level, pos);
            return saturate(moved);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (simulate) return saturate(source.extractableFe(level, pos, maxExtract));
            var moved = source.extractFe(level, pos, maxExtract);
            source.commit(level, pos);
            return saturate(moved);
        }
    }
    *///?}

    //? if forge {
    /*private static final class ForgeProvider implements net.minecraftforge.common.capabilities.ICapabilityProvider {

        private final EnergySource source;
        private final net.minecraft.world.level.block.entity.BlockEntity be;

        ForgeProvider(EnergySource source, net.minecraft.world.level.block.entity.BlockEntity be) {
            this.source = source;
            this.be = be;
        }

        @Override
        public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> capability,
                @Nullable Direction side) {
            if (capability != net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY
                    || !(be.getLevel() instanceof ServerLevel server)) {
                return net.minecraftforge.common.util.LazyOptional.empty();
            }
            var face = source.forFace(server, be.getBlockPos(), side);
            return face == null ? LazyOptional.empty()
                    : LazyOptional.of(() -> new Storage(face, server, be.getBlockPos(), side)).cast();
        }
    }

    private static final class Storage implements net.minecraftforge.energy.IEnergyStorage {

        private final EnergySource source;
        private final ServerLevel level;
        private final BlockPos pos;
        private final @Nullable Direction side;

        Storage(EnergySource source, ServerLevel level, BlockPos pos,
                @Nullable Direction side) {
            this.source = source;
            this.level = level;
            this.pos = pos;
            this.side = side;
        }

        @Override public int getEnergyStored() { return saturate(source.amountFe(level, pos)); }
        @Override public int getMaxEnergyStored() { return saturate(source.capacityFe(level, pos)); }
        @Override public boolean canReceive() { return source.insertableFe(level, pos) > 0; }
        @Override public boolean canExtract() { return source.extractableFe(level, pos) > 0; }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            if (simulate) return saturate(source.insertableFe(level, pos, maxReceive));
            var moved = source.insertFe(level, pos, side == null ? null : pos.relative(side), maxReceive);
            source.commit(level, pos);
            return saturate(moved);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (simulate) return saturate(source.extractableFe(level, pos, maxExtract));
            var moved = source.extractFe(level, pos, maxExtract);
            source.commit(level, pos);
            return saturate(moved);
        }
    }
    *///?}

    //? if forgelike && <26.1 {
    /*private static int saturate(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (value < 0L ? 0 : (int) value);
    }
    *///?}
}
