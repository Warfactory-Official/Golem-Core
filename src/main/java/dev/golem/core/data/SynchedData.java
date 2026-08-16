package dev.golem.core.data;

import net.minecraft.network.syncher.EntityDataAccessor;

/**
 * Stable definition surface for an entity's synchronized data.
 *
 * <p>Passed to {@link CompatEntity#defineData(SynchedData)} instead of exposing vanilla's changing
 * {@code defineSynchedData} argument list.
 */
public interface SynchedData {

    /** Defines an accessor and its initial value. */
    <T> void define(EntityDataAccessor<T> accessor, T initialValue);
}
