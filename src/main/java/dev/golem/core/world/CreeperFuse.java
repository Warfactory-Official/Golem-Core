package dev.golem.core.world;

/**
 * The creeper's private fuse counters, exposed by {@code MixinCreeperFuse} so a custom creeper can set
 * its own fuse length and detonate itself before vanilla's explosion path runs. Vanilla keeps
 * {@code swell}/{@code maxSwell} private on every version; the mixin is the loader-neutral widening.
 */
public interface CreeperFuse {

    int golem$swell();

    void golem$setSwell(int swell);

    int golem$maxSwell();

    void golem$setMaxSwell(int maxSwell);

    /** Sets the charged (lightning-struck) flag; the synched accessor is private on every version. */
    void golem$setPowered(boolean powered);
}
