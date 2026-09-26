package dev.golem.core.graph;

/**
 * What a claim or a face serves, as bits. {@code 1 << 7} upward is free for consumers (e.g. position-only links
 * in {@link EndpointRegistry#ofBeams}).
 */
public final class Domains {

    public static final int POWER_IN = 1;
    public static final int POWER_OUT = 1 << 1;
    public static final int FLUID_IN = 1 << 2;
    public static final int FLUID_OUT = 1 << 3;
    public static final int ITEMS = 1 << 4;
    /** Sided inventory at every connector cell, not only the core. */
    public static final int ITEMS_AT_CELLS = 1 << 5;
    /** Foreign energy (NeoForge/Forge capability); Fabric ships none. */
    public static final int FE = 1 << 6;
    public static final int FIRST_FREE = 1 << 7;

    /** Every face, as {@code 1 << Direction.ordinal()}. */
    public static final int FACES_ALL = 0b111111;

    private Domains() {}
}
