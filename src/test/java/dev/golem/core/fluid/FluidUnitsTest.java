package dev.golem.core.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** mB and droplets: 81,000 droplets to the bucket, so 81 to the mB, floored on the way back. */
class FluidUnitsTest {

    @Test
    void eightyOneDropletsPerMillibucket() {
        assertEquals(81L, FluidUnits.DROPLETS_PER_MB);
        assertEquals(81_000L, FluidUnits.toDroplets(1_000L), "one bucket");
        assertEquals(81L, FluidUnits.toDroplets(1L));
        assertEquals(0L, FluidUnits.toDroplets(0L));
        assertEquals(0L, FluidUnits.toDroplets(-5L), "a negative amount reads as none");
    }

    @Test
    void dropletsFloorToWholeMillibuckets() {
        assertEquals(1_000L, FluidUnits.toMbFloor(81_000L));
        assertEquals(0L, FluidUnits.toMbFloor(80L), "under one mB is no mB");
        assertEquals(1L, FluidUnits.toMbFloor(81L));
        assertEquals(1L, FluidUnits.toMbFloor(161L), "161 droplets is 1 mB and 80 left over");
        assertEquals(2L, FluidUnits.toMbFloor(162L));
        assertEquals(0L, FluidUnits.toMbFloor(-81L));
    }

    @Test
    void roundTripIsExactForWholeMillibuckets() {
        for (long mb : new long[]{1L, 7L, 250L, 1_000L, 16_000L, 256_000L, 1_000_000_000L}) {
            assertEquals(mb, FluidUnits.toMbFloor(FluidUnits.toDroplets(mb)), mb + " mB");
        }
    }

    @Test
    void saturatesAtTheEdges() {
        assertEquals(Long.MAX_VALUE, FluidUnits.toDroplets(Long.MAX_VALUE / 2));
        assertEquals(Integer.MAX_VALUE, FluidUnits.saturate(Long.MAX_VALUE));
        assertEquals(0, FluidUnits.saturate(-1L));
        assertEquals(42, FluidUnits.saturate(42L));
    }
}
