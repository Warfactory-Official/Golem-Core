package dev.golem.core.data;

//? if <26.1 {
/*import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;

import dev.golem.core.GolemCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/^*
 * {@link DataIn} backed by a {@code CompoundTag}, for versions before {@code ValueInput} existed.
 *
 * <p>The {@code *Or} accessors are the interesting part. Pre-26.1 {@code CompoundTag.getInt} returns
 * {@code 0} for a missing <i>or</i> wrong-typed key, so a naive backport would silently substitute
 * zero where the caller asked for a different default. Each accessor therefore checks {@code contains}
 * with the expected tag type first and only then reads, falling back to the caller's default —
 * reproducing 26.1's semantics rather than the old API's.
 ^/
public final class TagDataIn implements DataIn {

    // Tag type ids, as used by CompoundTag.contains(String, int).
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;

    private final CompoundTag tag;
    private final HolderLookup.@Nullable Provider registries;

    public TagDataIn(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    /^* The underlying tag, for vanilla helpers that take one directly. ^/
    public CompoundTag tag() {
        return tag;
    }

    /^* The registry provider, needed by pre-26.1 vanilla helpers that resolve item components. ^/
    public HolderLookup.@Nullable Provider registries() {
        return registries;
    }

    @Override
    public boolean getBooleanOr(String name, boolean defaultValue) {
        // Booleans are stored as bytes.
        return tag.contains(name, TAG_BYTE) ? tag.getBoolean(name) : defaultValue;
    }

    @Override
    public byte getByteOr(String name, byte defaultValue) {
        return tag.contains(name, TAG_BYTE) ? tag.getByte(name) : defaultValue;
    }

    @Override
    public short getShortOr(String name, short defaultValue) {
        return tag.contains(name, TAG_SHORT) ? tag.getShort(name) : defaultValue;
    }

    @Override
    public int getIntOr(String name, int defaultValue) {
        return tag.contains(name, TAG_INT) ? tag.getInt(name) : defaultValue;
    }

    @Override
    public long getLongOr(String name, long defaultValue) {
        return tag.contains(name, TAG_LONG) ? tag.getLong(name) : defaultValue;
    }

    @Override
    public float getFloatOr(String name, float defaultValue) {
        return tag.contains(name, TAG_FLOAT) ? tag.getFloat(name) : defaultValue;
    }

    @Override
    public double getDoubleOr(String name, double defaultValue) {
        return tag.contains(name, TAG_DOUBLE) ? tag.getDouble(name) : defaultValue;
    }

    @Override
    public String getStringOr(String name, String defaultValue) {
        return tag.contains(name, TAG_STRING) ? tag.getString(name) : defaultValue;
    }

    @Override
    public Optional<int[]> getIntArray(String name) {
        return tag.contains(name, TAG_INT_ARRAY) ? Optional.of(tag.getIntArray(name)) : Optional.empty();
    }

    @Override
    public <T> Optional<T> read(String name, Codec<T> codec) {
        if (!tag.contains(name)) return Optional.empty();
        return codec.parse(ops(), tag.get(name))
                .resultOrPartial(error -> GolemCore.LOG.error("Failed to decode '{}': {}", name, error));
    }

    @Override
    public Optional<DataIn> child(String name) {
        return tag.contains(name, TAG_COMPOUND)
                ? Optional.of(new TagDataIn(tag.getCompound(name), registries))
                : Optional.empty();
    }

    @Override
    public DataIn childOrEmpty(String name) {
        return new TagDataIn(tag.contains(name, TAG_COMPOUND) ? tag.getCompound(name) : new CompoundTag(), registries);
    }

    @Override
    public List<DataIn> childrenListOrEmpty(String name) {
        List<DataIn> out = new ArrayList<>();
        if (!tag.contains(name, TAG_LIST)) return out;
        ListTag list = tag.getList(name, TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            out.add(new TagDataIn(list.getCompound(i), registries));
        }
        return out;
    }

    private com.mojang.serialization.DynamicOps<Tag> ops() {
        return registries != null ? RegistryOps.create(NbtOps.INSTANCE, registries) : NbtOps.INSTANCE;
    }
}
*///?}
