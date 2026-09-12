package dev.golem.core.world;

/**
 * A loader-agnostic view over a foreign energy handler, in that handler's own unit (FE/RF).
 *
 * <p>Three shapes hide behind this. 26.1 NeoForge has {@code transfer.energy.EnergyHandler}, whose
 * insert and extract take a transaction rather than a simulate flag; 1.21.1 NeoForge and 1.20.1
 * Forge both have {@code IEnergyStorage}, reached through different capability tokens. Fabric has no
 * standard energy capability at all, so {@link EnergyCompat#find} answers null there and a network
 * sees no foreign peers.
 *
 * <p>Amounts are {@code long} because that is what the caller counts in; every implementation
 * saturates to {@code int} at the boundary, which is as wide as any of these APIs goes.
 */
public interface EnergyView {

    /**
     * The native handler, for dedup: one physical block answers on every face it exposes, and a
     * network must not count it once per face.
     */
    Object handlerIdentity();

    long amount();

    /**
     * Capacity, as a hint only — every one of these APIs permits a handler to over- or under-state
     * what an insert will actually accept.
     */
    long capacity();

    /** What this handler would accept right now, simulated. */
    long insertable();

    /** What this handler would give up right now, simulated. */
    long extractable();

    /** @return how much was actually accepted, between 0 and {@code maxFe}. */
    long insert(long maxFe);

    /** @return how much was actually taken, between 0 and {@code maxFe}. */
    long extract(long maxFe);

    default boolean canInsert() {
        return insertable() > 0;
    }

    default boolean canExtract() {
        return extractable() > 0;
    }
}
