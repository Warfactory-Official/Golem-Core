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

/^*
 * {@link DataOut} backed by a {@code CompoundTag}, for versions before {@code ValueOutput} existed.
 *
 * <p>The scalar puts map one-to-one. Two things need real work:
 *
 * <ul>
 *   <li><b>Codecs.</b> 26.1's {@code store} resolves registry-aware codecs internally. Pre-26.1 the
 *       caller must supply a {@code HolderLookup.Provider} and build a {@code RegistryOps}, so the
 *       provider is threaded in at construction. It is nullable because plenty of writes use codecs
 *       that touch no registries; those work with plain {@code NbtOps}.</li>
 *   <li><b>Encode failures.</b> {@code ValueOutput.store} swallows and reports errors rather than
 *       throwing. Matching that matters — a codec failure mid-save must not abort the whole block
 *       entity's write and corrupt the chunk — so failures are logged and the key is skipped.</li>
 * </ul>
 ^/
public final class TagDataOut implements DataOut {

    private final CompoundTag tag;
    private final HolderLookup.@Nullable Provider registries;

    public TagDataOut(CompoundTag tag, HolderLookup.@Nullable Provider registries) {
        this.tag = tag;
        this.registries = registries;
    }

    /^* The underlying tag, for handing back to vanilla's save path. ^/
    public CompoundTag tag() {
        return tag;
    }

    /^* The registry provider, needed by pre-26.1 vanilla helpers that resolve item components. ^/
    public HolderLookup.@Nullable Provider registries() {
        return registries;
    }

    @Override
    public void putBoolean(String name, boolean value) {
        tag.putBoolean(name, value);
    }

    @Override
    public void putByte(String name, byte value) {
        tag.putByte(name, value);
    }

    @Override
    public void putShort(String name, short value) {
        tag.putShort(name, value);
    }

    @Override
    public void putInt(String name, int value) {
        tag.putInt(name, value);
    }

    @Override
    public void putLong(String name, long value) {
        tag.putLong(name, value);
    }

    @Override
    public void putFloat(String name, float value) {
        tag.putFloat(name, value);
    }

    @Override
    public void putDouble(String name, double value) {
        tag.putDouble(name, value);
    }

    @Override
    public void putString(String name, String value) {
        tag.putString(name, value);
    }

    @Override
    public void putIntArray(String name, int[] value) {
        tag.putIntArray(name, value);
    }

    @Override
    public <T> void store(String name, Codec<T> codec, T value) {
        codec.encodeStart(ops(), value)
                .resultOrPartial(error -> GolemCore.LOG.error("Failed to encode '{}': {}", name, error))
                .ifPresent(encoded -> tag.put(name, encoded));
    }

    @Override
    public <T> void storeNullable(String name, Codec<T> codec, @Nullable T value) {
        if (value != null) {
            store(name, codec, value);
        }
    }

    @Override
    public DataOut child(String name) {
        // Attached up front so the child is present even if nothing is written into it, matching
        // ValueOutput.child, which always produces the key.
        CompoundTag child = new CompoundTag();
        tag.put(name, child);
        return new TagDataOut(child, registries);
    }

    @Override
    public ChildList childrenList(String name) {
        ListTag list = new ListTag();
        tag.put(name, list);
        return () -> {
            CompoundTag child = new CompoundTag();
            list.add(child);
            return new TagDataOut(child, registries);
        };
    }

    @Override
    public void discard(String name) {
        tag.remove(name);
    }

    private com.mojang.serialization.DynamicOps<Tag> ops() {
        return registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
    }
}
*///?}
