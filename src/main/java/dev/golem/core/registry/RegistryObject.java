package dev.golem.core.registry;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}
import net.minecraft.resources.ResourceKey;

/**
 * A lazily-resolved reference to a registered game object. This is the cross-loader analogue of
 * NeoForge's {@code DeferredHolder} / Forge's {@code RegistryObject}, but it is built purely on
 * vanilla APIs so it behaves identically on every loader.
 *
 * <p>The underlying value is looked up from the vanilla registries the first time {@link #get()} is
 * called, which must be after registration has run. Instances are produced by
 * {@link DeferredRegister#register(String, java.util.function.Function)}.
 */
public class RegistryObject<T> implements Supplier<T> {

    // Both keys are created on first use, not at registration: on 1.20.1 creating any ResourceKey runs
    // BuiltInRegistries' static initialiser, which throws outside a bootstrapped game, while 26.1 creates
    // keys freely. Deferring them lets a registration class run in a plain JVM on every node (Dejson's
    // build step), as it already can on 26.1. In game the first read resolves the same interned key the
    // eager version created, so nothing observable changes.
    private final Supplier<? extends ResourceKey<? extends Registry<?>>> registryKey;
    private final Supplier<? extends ResourceKey<?>> key;
    private T value;

    RegistryObject(Supplier<? extends ResourceKey<? extends Registry<?>>> registryKey, Supplier<? extends ResourceKey<?>> key) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.key = Objects.requireNonNull(key);
    }

    /**
     * A view of the same registration under a richer type: a consumer's registration handle that adds
     * declarations to the chain (HBM's {@code Reg.Handle}) extends this so it can stand wherever a
     * {@code RegistryObject} field or parameter already does. Resolves exactly as {@code source} would.
     */
    protected RegistryObject(RegistryObject<T> source) {
        this(source.registryKey, source.key);
    }

    /** The id ({@code namespace:path}) this object is registered under. */
    public
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            getId() {
        return dev.golem.core.Ids.keyId(key.get());
    }

    @SuppressWarnings("unchecked")
    public ResourceKey<T> getKey() {
        return (ResourceKey<T>) key.get();
    }

    /** Resolve the registered value, caching it after the first lookup. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public T get() {
        if (value == null) {
            //? if >=26.1 {
            Registry<?> registry = (Registry<?>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registryKey.get());
            value = (T) registry.getValueOrThrow((ResourceKey) key.get());
            //?} else {
            /*Registry<?> registry = (Registry<?>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) registryKey.get());
            value = (T) registry.getOrThrow((ResourceKey) key.get());
            *///?}
        }
        return value;
    }

    @Override
    public String toString() {
        return "RegistryObject[" + dev.golem.core.Ids.keyId(key.get()) + "]";
    }
}
