package dev.golem.core.world;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;

/** Codec-backed SavedData with stable lookup and serialization across SavedData API revisions. */
public abstract class CompatSavedData<T extends CompatSavedData<T>> extends SavedData {

    protected abstract Codec<T> codec();

    public static <T extends CompatSavedData<T>> T get(ServerLevel level, String id, Supplier<T> factory,
                                                          Codec<T> codec) {
        //? if >=26.1 {
        return level.getDataStorage().computeIfAbsent(new net.minecraft.world.level.saveddata.SavedDataType<>(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(id.substring(0, id.indexOf(':')),
                        id.substring(id.indexOf(':') + 1)), factory, codec,
                net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_MAP_DATA));
        //?} elif >=1.21 {
        /*return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(factory,
                        (tag, registries) -> codec.parse(
                                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries), tag)
                                .result().orElseGet(factory),
                        net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_MAP_DATA), legacyFileId(id));
        *///?} else {
        /*return level.getDataStorage().computeIfAbsent(
                tag -> codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElseGet(factory), factory, legacyFileId(id));
        *///?}
    }

    /** As {@link #get} but never creates: null when the level has no such data yet. */
    public static <T extends CompatSavedData<T>> @Nullable T getIfPresent(ServerLevel level, String id,
                                                                          Supplier<T> factory, Codec<T> codec) {
        //? if >=26.1 {
        return level.getDataStorage().get(new net.minecraft.world.level.saveddata.SavedDataType<>(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(id.substring(0, id.indexOf(':')),
                        id.substring(id.indexOf(':') + 1)), factory, codec,
                net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_MAP_DATA));
        //?} elif >=1.21 {
        /*return level.getDataStorage().get(
                new SavedData.Factory<>(factory,
                        (tag, registries) -> codec.parse(
                                net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries), tag)
                                .result().orElseGet(factory),
                        net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_MAP_DATA), legacyFileId(id));
        *///?} else {
        /*return level.getDataStorage().get(
                tag -> codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElseGet(factory), legacyFileId(id));
        *///?}
    }

    /**
     * Pre-26.1 SavedData uses this value as a filesystem basename. Keep a namespace in the identity while
     * replacing the colon Windows rejects; modern nodes retain the real {@code Identifier} above.
     */
    private static String legacyFileId(String id) {
        return id.replace(':', '_');
    }

    //? if >=1.21 && <26.1 {
    /*@Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        codec().encodeStart(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registries), self())
                .result().ifPresent(encoded -> {
                    if (encoded instanceof net.minecraft.nbt.CompoundTag compound) tag.merge(compound);
                });
        return tag;
    }

    @SuppressWarnings("unchecked")
    private T self() { return (T) this; }
    *///?} elif <1.21 {
    /*@Override
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) {
        codec().encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, self()).result().ifPresent(encoded -> {
            if (encoded instanceof net.minecraft.nbt.CompoundTag compound) tag.merge(compound);
        });
        return tag;
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
    *///?}
}
