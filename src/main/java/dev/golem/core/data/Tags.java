package dev.golem.core.data;

import net.minecraft.nbt.CompoundTag;

/**
 * Defaulted reads straight off a {@code CompoundTag}.
 *
 * <p>26.1 added {@code getIntOr}/{@code getStringOr} to {@code CompoundTag} itself, matching the
 * explicit-default style of {@code ValueInput}. Older versions only have {@code getInt}, which returns
 * a silent {@code 0} for a missing <i>or</i> wrong-typed key, the trap {@link TagDataIn} avoids; these
 * helpers type-check the same way.
 *
 * <p>These are for the handful of places that legitimately hold a raw {@code CompoundTag} (packet
 * payloads, item component data). Block-entity persistence should use {@link DataIn} instead.
 */
public final class Tags {

    /**
     * 26.1's {@code getIntOr}/{@code getLongOr}/... read any numeric tag ({@code NumericTag.asInt()} etc.), so a
     * short or byte where an int is asked for still answers; legacy {@code contains(name, 99)} is that same test.
     * (1.7 structure files store item ids as shorts.)
     */
    private static final int TAG_ANY_NUMERIC = 99;
    private static final int TAG_STRING = 8;
    private static final int TAG_INT_ARRAY = 11;

    private Tags() {}

    public static int getIntOr(CompoundTag tag, String name, int defaultValue) {
        //? if >=26.1 {
        return tag.getIntOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getInt(name) : defaultValue;
        *///?}
    }

    public static long getLongOr(CompoundTag tag, String name, long defaultValue) {
        //? if >=26.1 {
        return tag.getLongOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getLong(name) : defaultValue;
        *///?}
    }

    public static float getFloatOr(CompoundTag tag, String name, float defaultValue) {
        //? if >=26.1 {
        return tag.getFloatOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getFloat(name) : defaultValue;
        *///?}
    }

    public static double getDoubleOr(CompoundTag tag, String name, double defaultValue) {
        //? if >=26.1 {
        return tag.getDoubleOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getDouble(name) : defaultValue;
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
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getBoolean(name) : defaultValue;
        *///?}
    }

    public static byte getByteOr(CompoundTag tag, String name, byte defaultValue) {
        //? if >=26.1 {
        return tag.getByteOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC) ? tag.getByte(name) : defaultValue;
        *///?}
    }

    public static String getStringOr(CompoundTag tag, String name, String defaultValue) {
        //? if >=26.1 {
        return tag.getStringOr(name, defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_STRING) ? tag.getString(name) : defaultValue;
        *///?}
    }

    public static boolean hasInt(CompoundTag tag, String name) {
        //? if >=26.1 {
        return tag.getInt(name).isPresent();
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC);
        *///?}
    }

    public static boolean hasDouble(CompoundTag tag, String name) {
        //? if >=26.1 {
        return tag.getDouble(name).isPresent();
        //?} else {
        /*return tag.contains(name, TAG_ANY_NUMERIC);
        *///?}
    }

    public static int[] getIntArrayOr(CompoundTag tag, String name, int[] defaultValue) {
        //? if >=26.1 {
        return tag.getIntArray(name).orElse(defaultValue);
        //?} else {
        /*return tag.contains(name, TAG_INT_ARRAY) ? tag.getIntArray(name) : defaultValue;
        *///?}
    }

    public static java.util.Set<String> keys(CompoundTag tag) {
        //? if >=26.1 {
        return tag.keySet();
        //?} else {
        /*return tag.getAllKeys();
        *///?}
    }

    /** A compound-element list across the 26.1 typed-getter change (missing or wrongly typed: empty). */
    public static net.minecraft.nbt.ListTag getListOrEmpty(CompoundTag tag, String name) {
        //? if >=26.1 {
        return tag.getListOrEmpty(name);
        //?} else {
        /*return tag.getList(name, net.minecraft.nbt.Tag.TAG_COMPOUND);
        *///?}
    }

    /** The compound at {@code index} of a list, across the 26.1 typed-getter change. */
    public static CompoundTag getCompoundOrEmpty(net.minecraft.nbt.ListTag list, int index) {
        //? if >=26.1 {
        return list.getCompoundOrEmpty(index);
        //?} else {
        /*return list.getCompound(index);
        *///?}
    }

    /** Whether the tag holds {@code name} at all, across the key-lookup rename. */
    public static boolean contains(CompoundTag tag, String name) {
        return tag.contains(name);
    }

    /** Visits every entry of a compound (26.1 {@code CompoundTag.forEach}). */
    public static void forEach(CompoundTag tag, java.util.function.BiConsumer<String, net.minecraft.nbt.Tag> visitor) {
        for (String key : keys(tag)) {
            net.minecraft.nbt.Tag value = tag.get(key);
            if (value != null) visitor.accept(key, value);
        }
    }
}
