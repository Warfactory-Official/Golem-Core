package dev.golem.core.multiblock;

import dev.golem.core.graph.Domains;

/**
 * One commodity direction a folded multiblock's face can be open for. 1.7 states its geometry per CALL
 * SITE — {@code trySubscribe}/{@code tryProvide} against {@code sendFluid} — so a cell may serve one
 * commodity and not the other; a role-blind plane would union the two and open each on the other's cells.
 */
public enum CapRole {

    POWER_IN(Domains.POWER_IN, MultiblockCore.PASSIVE_POWER_IN),
    POWER_OUT(Domains.POWER_OUT, MultiblockCore.PASSIVE_NONE),
    FLUID_IN(Domains.FLUID_IN, MultiblockCore.PASSIVE_FLUID_IN),
    FLUID_OUT(Domains.FLUID_OUT, MultiblockCore.PASSIVE_NONE);

    /** No defensive copy per read: the active mask lookup runs per capability query. */
    public static final CapRole[] VALUES = values();
    public static final int COUNT = VALUES.length;

    public final int bit;
    public final int passivePlane;
    /** This role's active plane in {@link MultiblockMaskTable}. */
    public final int planeIndex;

    CapRole(int bit, int passivePlane) {
        this.bit = bit;
        this.passivePlane = passivePlane;
        this.planeIndex = ordinal();
    }
}
