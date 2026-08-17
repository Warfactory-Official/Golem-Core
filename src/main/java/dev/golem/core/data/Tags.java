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

    private static final int TAG_BYTE = 1;
    private static final int TAG_INT = 3;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_STRING = 8;

    private Tags() {}

    public static int getIntOr(CompoundTag tag, String name, int defaultValue) {
        //? if >=26.1 {
        return tag.getIntOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_INT) ? tag.getInt(name) : defaultValue;
        *///?}
    }

    public static float getFloatOr(CompoundTag tag, String name, float defaultValue) {
        //? if >=26.1 {
        return tag.getFloatOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_FLOAT) ? tag.getFloat(name) : defaultValue;
        *///?}
    }

    /**
     * A boolean is stored as a byte, so this reads the byte tag rather than asking for a boolean type
     * that does not exist in the NBT format.
     */
    public static boolean getBooleanOr(CompoundTag tag, String name, boolean defaultValue) {
        //? if >=26.1 {
        return tag.getBooleanOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_BYTE) ? tag.getBoolean(name) : defaultValue;
        *///?}
    }

    public static byte getByteOr(CompoundTag tag, String name, byte defaultValue) {
        //? if >=26.1 {
        return tag.getByteOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_BYTE) ? tag.getByte(name) : defaultValue;
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
