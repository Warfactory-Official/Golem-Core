package dev.golem.core.fluid;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * What a transactional peer (26.1 NeoForge's transfer API, Fabric's Transfer API) has moved across one
 * machine and not yet committed, in mB.
 *
 * <p>Nothing reaches the machine before the outermost transaction commits. A transactional peer
 * <em>simulates</em> by opening a transaction and aborting it, so applying at stage time would fire every
 * machine-level side effect once per probe. {@link FluidMachine#receive}/{@link FluidMachine#give} therefore
 * run in {@link #commit}, and everything before it is arithmetic over the machine's own reads.
 *
 * <p>One staging per {@linkplain FluidMachine#owner owner}, shared by every face of it: two faces asked in one
 * transaction must not each be told the whole demand. A tank already holding a staged crossing of one fluid
 * cannot take another in the same transaction, because the commit routes by fluid.
 */
final class FluidStaging {

    private final long[] delta;
    private final @Nullable Fluid[] staged;
    /** The face each tank's crossing was staged through, which commits it. */
    private final @Nullable FluidMachine[] via;

    FluidStaging(int tanks) {
        this.delta = new long[tanks];
        this.staged = new Fluid[tanks];
        this.via = new FluidMachine[tanks];
    }

    /**
     * The per-owner record a transactional backend keeps ({@code J} wraps a staging in that loader's journal
     * type), rebuilt when the owner's tank count changed.
     */
    static <J> J perOwner(Map<Object, WeakReference<J>> all, FluidMachine face,
                          java.util.function.ToIntFunction<J> tanksOf, java.util.function.IntFunction<J> create) {
        synchronized (all) {
            Object owner = face.owner();
            WeakReference<J> ref = all.get(owner);
            J journal = ref == null ? null : ref.get();
            if (journal == null || tanksOf.applyAsInt(journal) != face.tanks()) {
                // weak: a strong value would pin the block entity, and with it its level
                all.put(owner, new WeakReference<>(journal = create.apply(face.tanks())));
            }
            return journal;
        }
    }

    int tanks() {
        return delta.length;
    }

    private boolean inRange(FluidMachine face, int tank) {
        return tank >= 0 && tank < delta.length && face.tanks() == delta.length;
    }

    /** The fluid a peer reads at {@code tank}: the tank's own, or what is staged into an empty one. */
    Fluid fluid(FluidMachine face, int tank) {
        if (!inRange(face, tank)) return Fluids.EMPTY;
        if (amount(face, tank) <= 0L) return Fluids.EMPTY;
        Fluid content = face.fluid(tank);
        if (content != Fluids.EMPTY) return content;
        Fluid pending = staged[tank];
        return pending != null ? pending : Fluids.EMPTY;
    }

    long amount(FluidMachine face, int tank) {
        if (!inRange(face, tank)) return 0L;
        return Math.max(0L, face.amount(tank) + delta[tank]);
    }

    long capacity(FluidMachine face, int tank) {
        return inRange(face, tank) ? face.capacity(tank) : 0L;
    }

    private boolean addressable(FluidMachine face, int tank, Fluid fluid, boolean inserts) {
        if (!inRange(face, tank) || !face.valid() || fluid == Fluids.EMPTY) return false;
        if (!(inserts ? face.accepts(tank, fluid) : face.provides(tank, fluid))) return false;
        Fluid pending = staged[tank];
        return pending == null || pending == fluid;
    }

    /** The machine-level figures are per fluid, so a crossing staged on one tank is spent from all of them. */
    private long stagedFor(Fluid fluid, boolean inserts) {
        long total = 0L;
        for (int i = 0; i < delta.length; i++) {
            if (staged[i] != fluid) continue;
            if (inserts ? delta[i] > 0L : delta[i] < 0L) total += Math.abs(delta[i]);
        }
        return total;
    }

    /** mB of {@code fluid} {@code tank} would take now, staged crossings already deducted. */
    long insertable(FluidMachine face, int tank, Fluid fluid) {
        if (!addressable(face, tank, fluid, true)) return 0L;
        long room = face.capacity(tank) - face.amount(tank) - delta[tank];
        long demand = face.demand(fluid) - stagedFor(fluid, true);
        return Math.max(0L, Math.min(room, demand));
    }

    /** mB of {@code fluid} {@code tank} would give now, staged crossings already deducted. */
    long extractable(FluidMachine face, int tank, Fluid fluid) {
        if (!addressable(face, tank, fluid, false)) return 0L;
        long held = face.amount(tank) + delta[tank];
        long offered = face.available(fluid) - stagedFor(fluid, false);
        return Math.max(0L, Math.min(held, offered));
    }

    long stageInsert(FluidMachine face, int tank, Fluid fluid, long maxMb) {
        if (maxMb <= 0L) return 0L;
        long accepted = Math.min(maxMb, insertable(face, tank, fluid));
        if (accepted <= 0L) return 0L;
        delta[tank] += accepted;
        staged[tank] = fluid;
        via[tank] = face;
        return accepted;
    }

    long stageExtract(FluidMachine face, int tank, Fluid fluid, long maxMb) {
        if (maxMb <= 0L) return 0L;
        long given = Math.min(maxMb, extractable(face, tank, fluid));
        if (given <= 0L) return 0L;
        delta[tank] -= given;
        staged[tank] = fluid;
        via[tank] = face;
        return given;
    }

    /** Stages across every tank in order, as a sideless (non-indexed) insert does. */
    long stageInsertAny(FluidMachine face, Fluid fluid, long maxMb) {
        long moved = 0L;
        for (int i = 0; i < delta.length && moved < maxMb; i++) moved += stageInsert(face, i, fluid, maxMb - moved);
        return moved;
    }

    long stageExtractAny(FluidMachine face, Fluid fluid, long maxMb) {
        long moved = 0L;
        for (int i = 0; i < delta.length && moved < maxMb; i++) moved += stageExtract(face, i, fluid, maxMb - moved);
        return moved;
    }

    record Saved(long[] delta, @Nullable Fluid[] staged, @Nullable FluidMachine[] via) {}

    Saved save() {
        return new Saved(delta.clone(), staged.clone(), via.clone());
    }

    void restore(Saved saved) {
        System.arraycopy(saved.delta(), 0, delta, 0, delta.length);
        System.arraycopy(saved.staged(), 0, staged, 0, staged.length);
        System.arraycopy(saved.via(), 0, via, 0, via.length);
    }

    /**
     * Applies every staged crossing through the machine's own entry and exit points, then clears. Each figure
     * was clamped against the reads the machine answers now, so a short commit means a gate moved
     * mid-transaction, never an arithmetic slip.
     */
    void commit() {
        boolean moved = false;
        FluidMachine last = null;
        for (int i = 0; i < delta.length; i++) {
            Fluid fluid = staged[i];
            FluidMachine face = via[i];
            if (fluid == null || face == null || delta[i] == 0L || !face.valid()) continue;
            if (delta[i] > 0L) face.receive(fluid, delta[i]);
            else face.give(fluid, -delta[i]);
            moved = true;
            last = face;
        }
        Arrays.fill(delta, 0L);
        Arrays.fill(staged, null);
        Arrays.fill(via, null);
        if (moved) last.changed();
    }

    /** Drops every staged crossing without applying it. */
    void clear() {
        Arrays.fill(delta, 0L);
        Arrays.fill(staged, null);
        Arrays.fill(via, null);
    }

    // --- the simulate-flag loaders (1.21.1 NeoForge, 1.20.1 Forge): no transaction, so nothing is staged ---

    /** mB of {@code fluid} the machine would take through {@code face}, over every accepting tank. */
    static long insertableNow(FluidMachine face, Fluid fluid) {
        if (!face.valid() || fluid == Fluids.EMPTY) return 0L;
        long room = 0L;
        for (int i = 0; i < face.tanks(); i++) {
            if (face.accepts(i, fluid)) room += Math.max(0L, face.capacity(i) - face.amount(i));
        }
        return Math.max(0L, Math.min(room, face.demand(fluid)));
    }

    /** mB of {@code fluid} the machine would give through {@code face}, over every providing tank. */
    static long extractableNow(FluidMachine face, Fluid fluid) {
        if (!face.valid() || fluid == Fluids.EMPTY) return 0L;
        long held = 0L;
        for (int i = 0; i < face.tanks(); i++) {
            if (face.provides(i, fluid)) held += Math.max(0L, face.amount(i));
        }
        return Math.max(0L, Math.min(held, face.available(fluid)));
    }

    /** Inserts at once, clamped to {@link #insertableNow}; returns what the machine took. */
    static long insertNow(FluidMachine face, Fluid fluid, long maxMb, boolean simulate) {
        long amount = Math.min(maxMb, insertableNow(face, fluid));
        if (amount <= 0L) return 0L;
        if (simulate) return amount;
        long taken = face.receive(fluid, amount);
        if (taken > 0L) face.changed();
        return taken;
    }

    /** Extracts at once, clamped to {@link #extractableNow}; returns what the machine gave. */
    static long extractNow(FluidMachine face, Fluid fluid, long maxMb, boolean simulate) {
        long amount = Math.min(maxMb, extractableNow(face, fluid));
        if (amount <= 0L) return 0L;
        if (simulate) return amount;
        long given = face.give(fluid, amount);
        if (given > 0L) face.changed();
        return given;
    }
}
