package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Foreign energy at a block face, across the three capability systems this mod builds against.
 *
 * <p>26.1 NeoForge answers {@code Capabilities.Energy.BLOCK} with a
 * {@code transfer.energy.EnergyHandler} whose insert and extract are transactional (a simulation is
 * a transaction that is never committed). 1.21.1 NeoForge answers
 * {@code Capabilities.EnergyStorage.BLOCK} and 1.20.1 Forge answers {@code ForgeCapabilities.ENERGY}
 * through the block entity, both with an {@code IEnergyStorage} that takes a simulate flag instead.
 *
 * <p>Fabric ships no standard energy capability; there {@link #find} answers Team Reborn Energy's
 * {@code EnergyStorage.SIDED} when that optional mod is loaded ({@link RebornEnergy}), and null otherwise.
 */
public final class EnergyCompat {

    private EnergyCompat() {}

    /** Whether this node has a foreign energy system at all. */
    public static boolean supported() {
        //? if forgelike {
        return true;
        //?} else {
        /*return RebornEnergy.present(); // Fabric: only with the optional Team Reborn Energy
        *///?}
    }

    /** The foreign handler exposed at {@code pos} on {@code side}, or null if there is none. */
    public static @Nullable EnergyView find(Level level, BlockPos pos, @Nullable Direction side) {
        //? if neoforge && >=26.1 {
        net.neoforged.neoforge.transfer.energy.EnergyHandler handler =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, pos, side);
        return handler == null ? null : view(handler);
        //?} elif neoforge {
        /*net.neoforged.neoforge.energy.IEnergyStorage storage =
                level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, pos, side);
        return storage == null ? null : view(storage);
        *///?} elif forge {
        /*net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return null;
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, side)
                .map(EnergyCompat::view).orElse(null);
        *///?} else {
        /*return RebornEnergy.present() ? RebornEnergy.find(level, pos, side) : null;
        *///?}
    }

    /**
     * Tells the loader that what is exposed at {@code pos} may have changed. A block with no block
     * entity whose answer appears or disappears, a multiblock's dummy cell, has to say so itself,
     * because nothing else will.
     */
    public static void invalidate(Level level, BlockPos pos) {
        //? if neoforge {
        level.invalidateCapabilities(pos);
        //?}
    }

    //? if neoforge && >=26.1 {
    /**
     * A simulation is a transaction that is never committed; a real move commits it. Amounts saturate at
     * {@code int}, the width the handler's insert and extract take.
     */
    private static EnergyView view(net.neoforged.neoforge.transfer.energy.EnergyHandler handler) {
        return new EnergyView() {
            @Override public Object handlerIdentity() { return handler; }
            @Override public long amount() { return handler.getAmountAsLong(); }
            @Override public long capacity() { return handler.getCapacityAsLong(); }

            @Override
            public long insertable() {
                try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    return handler.insert(Integer.MAX_VALUE, transaction);
                }
            }

            @Override
            public long extractable() {
                try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    return handler.extract(Integer.MAX_VALUE, transaction);
                }
            }

            @Override
            public long insert(long maxFe) {
                try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    int moved = handler.insert(saturate(maxFe), transaction);
                    transaction.commit();
                    return moved;
                }
            }

            @Override
            public long extract(long maxFe) {
                try (net.neoforged.neoforge.transfer.transaction.Transaction transaction =
                             net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    int moved = handler.extract(saturate(maxFe), transaction);
                    transaction.commit();
                    return moved;
                }
            }
        };
    }
    //?}

    //? if forgelike && <26.1 {
    /*private static EnergyView view(
            //? if neoforge {
            /^net.neoforged.neoforge.energy.IEnergyStorage storage
            ^///?} elif forge {
            net.minecraftforge.energy.IEnergyStorage storage
            //?}
    ) {
        return new EnergyView() {
            @Override public Object handlerIdentity() { return storage; }
            @Override public long amount() { return storage.getEnergyStored(); }
            @Override public long capacity() { return storage.getMaxEnergyStored(); }
            @Override public long insertable() { return storage.receiveEnergy(Integer.MAX_VALUE, true); }
            @Override public long extractable() { return storage.extractEnergy(Integer.MAX_VALUE, true); }
            @Override public long insert(long maxFe) { return storage.receiveEnergy(saturate(maxFe), false); }
            @Override public long extract(long maxFe) { return storage.extractEnergy(saturate(maxFe), false); }
        };
    }
    *///?}

    //? if forgelike {
    private static int saturate(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (value < 0L ? 0 : (int) value);
    }
    //?}
}
