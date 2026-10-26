package dev.golem.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * What a mod exposes to foreign energy systems at one of its own block positions, in that system's
 * own unit (FE/RF). The counterpart of {@link EnergyView}: that one reads somebody else's handler,
 * this one is read as somebody else's handler.
 *
 * <p>Every method is asked on the server thread for a position the implementor owns. A position that
 * exposes nothing answers zero to both budgets, and the loader is free to stop asking.
 *
 * <p>{@link #snapshot} and {@link #restore} exist for 26.1, where an insert or extract happens inside
 * a transaction the caller may roll back <em>after</em> it returned. Everything the two calls touched
 * has to be restorable, or a rolled-back transaction leaves energy behind. On the older lines a
 * simulate flag asks the question without changing anything and neither is called.
 */
public interface EnergySource {

    /** Binds one real face, or refuses it. Position-only sources retain their existing behavior. */
    default @Nullable EnergySource forFace(ServerLevel level, BlockPos pos, @Nullable Direction side) { return this; }

    /** Resolves the exact owner for one mutation; retained views may observe a new owner on the next call. */
    default @Nullable EnergySource forOperation(ServerLevel level, BlockPos pos) { return this; }

    /** Releases one loader-owned snapshot after its rollback, promotion or final commit is complete. */
    default void releaseSnapshot(ServerLevel level, BlockPos pos, Object state) {}

    /** Applies staged work on final commit; immediate sources have no additional work. */
    default void commit(ServerLevel level, BlockPos pos) {}

    /** Shared owner identity for transactional snapshots; null retains a journal per exposed view. */
    default @Nullable Object transactionKey() { return null; }

    /** Quotes a bounded request; face sources can retain indivisible conversion quanta. */
    default long insertableFe(ServerLevel level, BlockPos pos, long maximumFe) {
        return Math.max(0L, Math.min(maximumFe, insertableFe(level, pos)));
    }
    default long extractableFe(ServerLevel level, BlockPos pos, long maximumFe) {
        return Math.max(0L, Math.min(maximumFe, extractableFe(level, pos)));
    }

    /** What this position holds, as a peer reads it. */
    long amountFe(ServerLevel level, BlockPos pos);

    /**
     * Capacity, as a hint. A peer may compute free space as capacity minus amount, so this should be
     * amount plus what an insert would really take.
     */
    long capacityFe(ServerLevel level, BlockPos pos);

    /** What an insert would take right now, without taking it. */
    long insertableFe(ServerLevel level, BlockPos pos);

    /** What an extract would give up right now, without giving it. */
    long extractableFe(ServerLevel level, BlockPos pos);

    /**
     * @param from the position pushing at us, or null when the caller is sideless; an
     *             implementor uses it to tell one peer from another.
     * @return how much was actually taken.
     */
    long insertFe(ServerLevel level, BlockPos pos, @Nullable BlockPos from, long fe);

    /** @return how much was actually given up. */
    long extractFe(ServerLevel level, BlockPos pos, long fe);

    /** Everything {@link #insertFe} and {@link #extractFe} can change at this position. */
    Object snapshot(ServerLevel level, BlockPos pos);

    /** Puts back what {@link #snapshot} recorded, because the caller's transaction did not commit. */
    void restore(ServerLevel level, BlockPos pos, Object state);
}
