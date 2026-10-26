package dev.golem.core.world;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;

/** Registry-holder resolution whose lookup spelling changed across the supported range. */
public final class RegistryLookups {

    private RegistryLookups() {}

    /** Resolves a registered key from live dynamic registry access. */
    public static <T> Holder<T> holder(RegistryAccess registries, ResourceKey<Registry<T>> registry, ResourceKey<T> key) {
        //? if >=1.21.1 {
        return registries.lookupOrThrow(registry).getOrThrow(key);
        //?} else {
        /*return registries.registryOrThrow(registry).getHolderOrThrow(key);
        *///?}
    }
}
