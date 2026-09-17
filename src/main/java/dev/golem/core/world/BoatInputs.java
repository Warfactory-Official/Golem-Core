package dev.golem.core.world;

/** The boat's private rider-input flags, exposed by {@code MixinBoatControl}. */
public interface BoatInputs {

    boolean golem$inputLeft();

    boolean golem$inputRight();

    boolean golem$inputUp();

    boolean golem$inputDown();
}
