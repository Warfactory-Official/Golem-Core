package dev.golem.core.world;

/**
 * Fabric's energy peer: Team Reborn Energy ({@code team_reborn_energy}, the API Tech Reborn and most Fabric tech
 * mods speak), an <b>optional</b> dependency. Core compiles against it and never ships it; everything here is
 * reached only after {@link #present()} said the mod is loaded, so no {@code team.reborn} class is ever resolved
 * on a game without it.
 *
 * <p>The unit is taken as FE 1:1, the conversion every Fabric/Forge bridge uses. Its insert and extract run inside a
 * Fabric transfer transaction the caller may abort after the call returned, so the exposed handler journals
 * through {@link EnergySource#snapshot}/{@link EnergySource#restore} exactly like 26.1 NeoForge's.
 *
 * <p>Not present on the forge-family nodes (they have the loader's own energy capability).
 */
public final class RebornEnergy {

    private RebornEnergy() {}

    /** Whether Team Reborn Energy is loaded on this (Fabric) game. */
    public static boolean present() {
        //? if fabric {
        /*return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("team_reborn_energy");
        *///?} else {
        return false;
        //?}
    }

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
