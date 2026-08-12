package dev.golem.core.data;

import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Optional;

/**
 * Read side of block-entity persistence. The counterpart to {@link DataOut}.
 *
 * <p>Mirrors 26.1's {@code ValueInput}. Note the {@code *Or} accessors: 26.1 made the default value
 * explicit at every call site, where the old {@code CompoundTag} API returned a silent zero/empty for
 * a missing key. Keeping the 26.1 spelling means the backport cannot quietly reintroduce that
 * ambiguity — the default is always stated, on every version.
 */
public interface DataIn {

    boolean getBooleanOr(String name, boolean defaultValue);

    byte getByteOr(String name, byte defaultValue);

    short getShortOr(String name, short defaultValue);

    int getIntOr(String name, int defaultValue);

    long getLongOr(String name, long defaultValue);

    float getFloatOr(String name, float defaultValue);

    double getDoubleOr(String name, double defaultValue);

    String getStringOr(String name, String defaultValue);

    Optional<int[]> getIntArray(String name);

    /** Decodes a value with a codec, empty if absent or malformed. */
    <T> Optional<T> read(String name, Codec<T> codec);

    /** A nested compound, empty if absent. */
    Optional<DataIn> child(String name);

    /** A nested compound, or an empty one if absent — for the common read-with-defaults case. */
    DataIn childOrEmpty(String name);

    /** The child compounds under a key, empty list if absent. */
    List<DataIn> childrenListOrEmpty(String name);
}
