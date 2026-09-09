// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

public interface DistanceUpdateLimiter {

    void tick();

    boolean shouldUpdate(double distanceSquared);

    DistanceUpdateLimiter NONE = new DistanceUpdateLimiter() {
        @Override public void tick() {}
        @Override public boolean shouldUpdate(double distanceSquared) { return true; }
    };

    static DistanceUpdateLimiter bandedPrime() {
        return new BandedPrime();
    }

    final class BandedPrime implements DistanceUpdateLimiter {
        private static final int[] DIVISORS = {1, 2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31};
        private int tickCount;

        @Override
        public void tick() {
            tickCount++;
        }

        @Override
        public boolean shouldUpdate(double distanceSquared) {
            int band = (int) (distanceSquared / 2048.0);
            if (band < 0) band = 0;
            if (band >= DIVISORS.length) band = DIVISORS.length - 1;
            int divisor = DIVISORS[band];
            return (tickCount % divisor) == 0;
        }
    }
}
//?}
