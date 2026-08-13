package dev.golem.core.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Defaulted reads straight off a {@code CompoundTag}.
 *
 * <p>26.1 added {@code getIntOr}/{@code getStringOr} to {@code CompoundTag} itself, matching the
 * explicit-default style of {@code ValueInput}. Older versions only have {@code getInt}, which returns
 * a silent {@code 0} for a missing <i>or</i> wrong-typed key — the same trap {@link TagDataIn} exists
 * to avoid, so the same type-checked treatment is applied here.
 *
 * <p>These are for the handful of places that legitimately hold a raw {@code CompoundTag} (packet
 * payloads, item component data). Block-entity persistence should use {@link DataIn} instead.
 */
public final class Tags {

    private static final int TAG_INT = 3;
    private static final int TAG_STRING = 8;

    private Tags() {}

    public static int getIntOr(CompoundTag tag, String name, int defaultValue) {
        //? if >=26.1 {
        return tag.getIntOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_INT) ? tag.getInt(name) : defaultValue;
        *///?}
    }

    public static String getStringOr(CompoundTag tag, String name, String defaultValue) {
        //? if >=26.1 {
        return tag.getStringOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_STRING) ? tag.getString(name) : defaultValue;
        *///?}
    }
}
