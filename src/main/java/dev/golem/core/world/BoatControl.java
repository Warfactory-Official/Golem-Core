package dev.golem.core.world;

/**
 * A boat that steers itself. Vanilla's {@code controlBoat} is private on every version;
 * {@code MixinBoatControl} routes the vanilla call into {@link #golem$controlBoat()} for boats
 * implementing this. The input flags it needs come from {@link BoatInputs}.
 */
public interface BoatControl {

    /** Replaces vanilla's steering for this tick. */
    void golem$controlBoat();
}
