package dev.golem.core.world;

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
 * <p>The three systems want three different things, and the difference is not cosmetic. 26.1 NeoForge
 * takes a {@code transfer.energy.EnergyHandler} whose insert and extract run inside a transaction that
 * the caller may roll back after the call returned, so the handler has to be able to put back what it
 * changed — which is why {@link EnergySource} carries a snapshot pair. 1.21.1 NeoForge and 1.20.1
 * Forge take an {@code IEnergyStorage}, whose simulate flag asks the same question without changing
 * anything, and the snapshot pair is never used there.
 *
 * <p>Fabric ships no standard energy capability, so {@link #expose} does nothing and nothing on that
 * node can see our energy. That is the same answer {@link EnergyCompat} gives in the other direction,
 * and for the same reason: picking a third-party API on the mod's behalf is not this layer's call.
 *
 * <p>Call this once, while the mod bus is still accepting listeners — the loaders that have a
 * registration event subscribe to it here.
 */
public final class EnergyExposure {

    private EnergyExposure() {}

    /** Whether anything on this node can see what {@link #expose} publishes. */
    public static boolean supported() {
        //? if forgelike {
        return true;
        //?} else {
        /*return false;
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
                                (level, pos, state, be, side) -> level instanceof ServerLevel server
                                        ? new Journalled(source, server, pos.immutable(), side) : null,
                                blocks.get()));
        //?} elif neoforge {
        /*dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                        event.registerBlock(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                                (level, pos, state, be, side) -> level instanceof ServerLevel server
                                        ? new Simulated(source, server, pos.immutable(), side) : null,
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
        *///?}
    }

    //? if neoforge && >=26.1 {
    /**
     * A handler whose every mutation is journalled, because 26.1 lets a caller open a transaction,
     * take energy, and then abort — at which point what we handed over has to come back.
     */
    private static final class Journalled
            extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Object>
            implements net.neoforged.neoforge.transfer.energy.EnergyHandler {

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
        }

        @Override protected Object createSnapshot() { return source.snapshot(level, pos); }
        @Override protected void revertToSnapshot(Object state) { source.restore(level, pos, state); }

        @Override public long getAmountAsLong() { return source.amountFe(level, pos); }
        @Override public long getCapacityAsLong() { return source.capacityFe(level, pos); }

        @Override
        public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0) return 0;
            updateSnapshots(transaction);
            return (int) source.insertFe(level, pos, side == null ? null : pos.relative(side), amount);
        }

        @Override
        public int extract(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0) return 0;
            updateSnapshots(transaction);
            return (int) source.extractFe(level, pos, amount);
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
            if (simulate) return saturate(Math.min(maxReceive, source.insertableFe(level, pos)));
            return saturate(source.insertFe(level, pos, side == null ? null : pos.relative(side), maxReceive));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (simulate) return saturate(Math.min(maxExtract, source.extractableFe(level, pos)));
            return saturate(source.extractFe(level, pos, maxExtract));
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
            return net.minecraftforge.common.util.LazyOptional
                    .of(() -> new Storage(source, server, be.getBlockPos(), side)).cast();
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
            if (simulate) return saturate(Math.min(maxReceive, source.insertableFe(level, pos)));
            return saturate(source.insertFe(level, pos, side == null ? null : pos.relative(side), maxReceive));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            if (simulate) return saturate(Math.min(maxExtract, source.extractableFe(level, pos)));
            return saturate(source.extractFe(level, pos, maxExtract));
        }
    }
    *///?}

    //? if forgelike && <26.1 {
    /*private static int saturate(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (value < 0L ? 0 : (int) value);
    }
    *///?}
}
