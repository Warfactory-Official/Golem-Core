package dev.golem.core.world;

/**
 * The Team Reborn Energy half of {@link RebornEnergy}, in its own class so that nothing names a {@code team.reborn} type
 * until {@link RebornEnergy#present()} has said the mod is loaded. Merging them broke every Fabric game without the
 * mod: verifying the merged class (on the first {@code present()} call) checked that {@code Journalled} is an
 * {@code EnergyStorage}, which loaded {@code team.reborn.energy.api.EnergyStorage} and failed with
 * {@code NoClassDefFoundError} before {@code present()} could answer. Only {@link RebornEnergy} calls this class.
 */
final class RebornEnergyBridge {

    private RebornEnergyBridge() {}

    //? if fabric {
    /*static @org.jspecify.annotations.Nullable EnergyView find(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.core.@org.jspecify.annotations.Nullable Direction side) {
        team.reborn.energy.api.EnergyStorage storage = team.reborn.energy.api.EnergyStorage.SIDED.find(level, pos, side);
        return storage == null ? null : view(storage);
    }

    private static EnergyView view(team.reborn.energy.api.EnergyStorage storage) {
        return new EnergyView() {
            @Override public Object handlerIdentity() { return storage; }
            @Override public long amount() { return storage.getAmount(); }
            @Override public long capacity() { return storage.getCapacity(); }

            @Override
            public long insertable() {
                if (!storage.supportsInsertion()) return 0;
                try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction t =
                             net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    return storage.insert(Integer.MAX_VALUE, t);
                }
            }

            @Override
            public long extractable() {
                if (!storage.supportsExtraction()) return 0;
                try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction t =
                             net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    return storage.extract(Integer.MAX_VALUE, t);
                }
            }

            @Override
            public long insert(long maxFe) {
                if (maxFe <= 0 || !storage.supportsInsertion()) return 0;
                try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction t =
                             net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    long moved = storage.insert(maxFe, t);
                    t.commit();
                    return moved;
                }
            }

            @Override
            public long extract(long maxFe) {
                if (maxFe <= 0 || !storage.supportsExtraction()) return 0;
                try (net.fabricmc.fabric.api.transfer.v1.transaction.Transaction t =
                             net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                    long moved = storage.extract(maxFe, t);
                    t.commit();
                    return moved;
                }
            }
        };
    }

    /^*
     * Registers {@code source} as the energy storage of every block in {@code blocks}. A fallback provider, because
     * Fabric may run this before the consumer's blocks are registered: the block set is resolved on first use.
     ^/
    static void expose(EnergySource source, java.util.function.Supplier<net.minecraft.world.level.block.Block[]> blocks) {
        java.util.Set<net.minecraft.world.level.block.Block>[] carriers = new java.util.Set[1];
        team.reborn.energy.api.EnergyStorage.SIDED.registerFallback((level, pos, state, be, side) -> {
            if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return null;
            if (carriers[0] == null) carriers[0] = java.util.Set.of(blocks.get());
            if (!carriers[0].contains(state.getBlock())) return null;
            return new Journalled(source, server, pos.immutable(), side);
        });
    }

    private static final class Journalled
            extends net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant<Object>
            implements team.reborn.energy.api.EnergyStorage {

        private final EnergySource source;
        private final net.minecraft.server.level.ServerLevel level;
        private final net.minecraft.core.BlockPos pos;
        private final net.minecraft.core.@org.jspecify.annotations.Nullable Direction side;

        Journalled(EnergySource source, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos,
                   net.minecraft.core.@org.jspecify.annotations.Nullable Direction side) {
            this.source = source;
            this.level = level;
            this.pos = pos;
            this.side = side;
        }

        @Override protected Object createSnapshot() { return source.snapshot(level, pos); }
        @Override protected void readSnapshot(Object state) { source.restore(level, pos, state); }

        @Override public long getAmount() { return source.amountFe(level, pos); }
        @Override public long getCapacity() { return source.capacityFe(level, pos); }
        @Override public boolean supportsInsertion() { return source.insertableFe(level, pos) > 0; }
        @Override public boolean supportsExtraction() { return source.extractableFe(level, pos) > 0; }

        @Override
        public long insert(long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            updateSnapshots(transaction);
            return source.insertFe(level, pos, side == null ? null : pos.relative(side), maxAmount);
        }

        @Override
        public long extract(long maxAmount, net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
            if (maxAmount <= 0) return 0;
            updateSnapshots(transaction);
            return source.extractFe(level, pos, maxAmount);
        }
    }
    *///?}
}
