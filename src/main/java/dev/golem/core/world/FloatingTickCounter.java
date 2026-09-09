package dev.golem.core.world;

/** Internal duck contract for resetting the pre-26.1 anti-floating counter. */
public interface FloatingTickCounter {
    void resetFloatingTickCount();
}
