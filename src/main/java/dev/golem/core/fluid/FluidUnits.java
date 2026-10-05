package dev.golem.core.fluid;

/**
 * The two units foreign fluid systems count in. NeoForge and Forge count millibuckets (mB), as a mod's own
 * tanks do; Fabric's Transfer API counts droplets, 81,000 to the bucket, so one mB is 81 droplets.
 *
 * <p>A droplet amount that is not a whole number of mB is floored: a tank that stores mB cannot take or give
 * the fraction, and a peer re-offers the remainder, so flooring costs a retry rather than fluid.
 */
public final class FluidUnits {

    private FluidUnits() {}

    /** Fabric's {@code FluidConstants.BUCKET}. */
    public static final long DROPLETS_PER_BUCKET = 81_000L;
    public static final long MB_PER_BUCKET = 1_000L;
    /** {@code 81}: droplets in one mB. */
    public static final long DROPLETS_PER_MB = DROPLETS_PER_BUCKET / MB_PER_BUCKET;

    /** mB to droplets, saturating at {@link Long#MAX_VALUE}; a negative amount reads as 0. */
    public static long toDroplets(long mb) {
        if (mb <= 0L) return 0L;
        return mb > Long.MAX_VALUE / DROPLETS_PER_MB ? Long.MAX_VALUE : mb * DROPLETS_PER_MB;
    }

    /** Droplets to whole mB, floored; a negative amount reads as 0. */
    public static long toMbFloor(long droplets) {
        return droplets <= 0L ? 0L : droplets / DROPLETS_PER_MB;
    }

    /** {@code value} clamped into an {@code int}, for the APIs whose amounts are {@code int}s. */
    public static int saturate(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (value < 0L ? 0 : (int) value);
    }
}
