package dev.golem.core.data;

import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;

/**
 * Write side of block-entity persistence.
 *
 * <p>Mirrors 26.1's {@code ValueOutput}, which replaced writing straight into a {@code CompoundTag}.
 * The shapes differ in three ways that matter, and this facade hides all of them:
 *
 * <ul>
 *   <li>26.1 writes through an interface that can report problems and be backed by formats other than
 *       NBT; older versions mutate a {@code CompoundTag} directly.</li>
 *   <li>Codec-based storage on 26.1 goes through {@link #store}; older versions have to encode against
 *       a registry lookup that the caller must thread through by hand.</li>
 *   <li>Nested compounds are created by {@link #child} on 26.1, versus building a tag and putting it
 *       back on older versions, which is easy to get wrong when the child ends up empty.</li>
 * </ul>
 *
 * <p>Only the operations HBM uses are exposed, not vanilla's full surface.
 */
public interface DataOut {

    void putBoolean(String name, boolean value);

    void putByte(String name, byte value);

    void putShort(String name, short value);

    void putInt(String name, int value);

    void putLong(String name, long value);

    void putFloat(String name, float value);

    void putDouble(String name, double value);

    void putString(String name, String value);

    void putIntArray(String name, int[] value);

    /** Encodes a value with a codec. */
    <T> void store(String name, Codec<T> codec, T value);

    /** Encodes a value with a codec, writing nothing when it is null. */
    <T> void storeNullable(String name, Codec<T> codec, @Nullable T value);

    /** A nested compound under this one. */
    DataOut child(String name);

    /** A list of nested compounds under this one. */
    ChildList childrenList(String name);

    /** Removes a key. */
    void discard(String name);

    /** Append-only list of child compounds, mirroring {@code ValueOutput.ValueOutputList}. */
    interface ChildList {
        DataOut addChild();
    }
}
