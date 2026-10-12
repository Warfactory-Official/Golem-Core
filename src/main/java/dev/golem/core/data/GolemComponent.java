package dev.golem.core.data;

import com.mojang.serialization.Codec;
import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

//? if >=1.21.1 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
//?}

/**
 * A typed item value which is a data component on modern Minecraft and namespaced item NBT on 1.20.1.
 *
 * <p>Declare these as static fields before content registration is flushed:
 * {@code GolemComponent.integer("example", "charge")}. Modern component registrations are added to
 * Golem Core's normal {@link DeferredRegister} queue automatically.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GolemComponent<T> {

    //? if >=1.21.1 {
    private static final Map<String, DeferredRegister<DataComponentType<?>>> REGISTERS = new HashMap<>();
    //?}

    /**
     * Every component id declared through this facade, in declaration order.
     *
     * <p>A component's id is a string in the places that name one from data — a recipe matching an
     * ingredient on {@code hbm:item_variant}, or handing {@code hbm:heat} to its result — and below
     * 1.20.5 there is no component registry for such a name to be wrong against. This is what a
     * consumer can hold those names up to on every target.
     */
    private static final java.util.Set<String> KEYS =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /** Every declared component by its id, for {@link #legacyJson}. */
    private static final Map<String, GolemComponent<?>> BY_KEY = new java.util.concurrent.ConcurrentHashMap<>();

    private final String key;
    //? if >=1.21.1 {
    // The registered type may hold a different value class than T: a component whose natural value
    // is mutable (an ItemStack) is stored as an immutable wrapper, since the platform validates that
    // component values implement equals/hashCode. toStored/fromStored bridge the two; identity by default.
    private final RegistryObject<DataComponentType<Object>> component;
    private final java.util.function.Function<T, Object> toStored;
    private final java.util.function.Function<Object, T> fromStored;
    //?} else {
    /*private final Supplier<Codec<T>> codec;
    private final LegacyValue<T> legacy;
    *///?}

    private GolemComponent(String namespace, String name, Supplier<Codec<T>> codec) {
        this(namespace, name, (Supplier) codec, value -> value, stored -> (T) stored);
    }

    /**
     * A component stored as {@code S} on modern platforms while exposing {@code T}; see the field note.
     * The codec is read only when first needed (the registry flush on modern platforms, the first
     * read or write on 1.20.1), so declaring a component never initialises its codec's class.
     */
    private <S> GolemComponent(String namespace, String name, Supplier<Codec<S>> storedCodec,
                               java.util.function.Function<T, S> toStored, java.util.function.Function<S, T> fromStored) {
        key = declare(namespace, name);
        BY_KEY.put(key, this);
        //? if >=1.21.1 {
        // The registry is named on first use, as every Golem register's is (see RegistryObject).
        DeferredRegister<DataComponentType<?>> register = REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(() -> Registries.DATA_COMPONENT_TYPE, ns));
        component = register.register(name, () -> (DataComponentType) componentType(storedCodec.get()));
        this.toStored = (java.util.function.Function) toStored;
        this.fromStored = (java.util.function.Function) fromStored;
        //?} else {
        /*this.codec = memoize((Supplier) storedCodec);
        legacy = null;
        *///?}
    }

    /** Memoises {@code source}; a race computes the same codec twice, which is harmless. */
    private static <C> Supplier<C> memoize(Supplier<C> source) {
        return new Supplier<>() {
            private volatile C value;

            @Override
            public C get() {
                C resolved = value;
                if (resolved == null) {
                    value = resolved = Objects.requireNonNull(source.get(), "codec");
                }
                return resolved;
            }
        };
    }

    //? if >=1.21.1 {
    static <T> DataComponentType<T> componentType(Codec<T> codec) {
        return DataComponentType.<T>builder().persistent(codec).networkSynchronized(
                net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(codec)).build();
    }
    //?}

    //? if <1.21.1 {
    /*private GolemComponent(String namespace, String name, LegacyValue<T> legacy) {
        key = declare(namespace, name);
        BY_KEY.put(key, this);
        codec = null;
        this.legacy = legacy;
    }
    *///?}

    private static String declare(String namespace, String name) {
        String key = Ids.of(namespace, name).toString();
        KEYS.add(key);
        return key;
    }

    /** The id this component is registered under, {@code namespace:name}. */
    public String key() {
        return key;
    }

    /**
     * Below 1.21.1, the value a 1.20.1 item tag holds under a declared component's {@code key}, re-encoded with that
     * component's codec as the component JSON of newer versions spells it (NBT has no boolean: 1.20.1 stores one as a
     * byte). Empty for a key no component declared, for a stack-valued component, and on newer versions.
     */
    public static Optional<com.google.gson.JsonElement> legacyJson(String key, net.minecraft.nbt.Tag value) {
        GolemComponent<?> component = BY_KEY.get(key);
        return component == null || value == null ? Optional.empty() : component.legacyJsonOf(value);
    }

    private Optional<com.google.gson.JsonElement> legacyJsonOf(net.minecraft.nbt.Tag value) {
        //? if >=1.21.1 {
        return Optional.empty();
        //?} else {
        /*if (legacy != null) return Optional.empty();
        return codec.get().parse(NbtOps.INSTANCE, value).result()
                .flatMap(decoded -> codec.get().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, decoded).result());
        *///?}
    }

    /** Every component id declared through this facade so far. */
    public static java.util.Set<String> declaredKeys() {
        synchronized (KEYS) {
            return java.util.Set.copyOf(KEYS);
        }
    }

    /** Creates a component backed by the supplied persistent codec. */
    public static <T> GolemComponent<T> of(String namespace, String name, Codec<T> codec) {
        Objects.requireNonNull(codec);
        return new GolemComponent<>(namespace, name, () -> codec);
    }

    /**
     * As {@link #of(String, String, Codec)}, with a codec that is read only when first needed:
     * {@code of(ns, "box", () -> ItemContents.CODEC)}. Use it for a codec built on registry-backed codecs
     * ({@code ItemStack.CODEC}, ...): on 1.20.1 initialising one outside a bootstrapped game fails, and a
     * registration class that declares the component must still initialise in a plain JVM. The component
     * is declared and registered at this call, exactly as with the eager form.
     */
    public static <T> GolemComponent<T> of(String namespace, String name, Supplier<Codec<T>> codec) {
        return new GolemComponent<>(namespace, name, Objects.requireNonNull(codec));
    }

    public static GolemComponent<String> string(String namespace, String name) {
        return of(namespace, name, Codec.STRING);
    }

    public static GolemComponent<Integer> integer(String namespace, String name) {
        return of(namespace, name, Codec.INT);
    }

    public static GolemComponent<Long> longValue(String namespace, String name) {
        return of(namespace, name, Codec.LONG);
    }

    public static GolemComponent<Float> floatValue(String namespace, String name) {
        return of(namespace, name, Codec.FLOAT);
    }

    public static GolemComponent<CompoundTag> compound(String namespace, String name) {
        return of(namespace, name, CompoundTag.CODEC);
    }

    //? if >=1.21.1 {
    /**
     * Native component handle for integrations that explicitly consume vanilla component types. Its value
     * class is the stored one, which differs from {@code T} only for {@link #itemStack}.
     */
    public DataComponentType<T> type() {
        return (DataComponentType) component.get();
    }
    //?}

    /**
     * A component holding one stack, including all of its item data.
     *
     * <p>Modern platforms store it as a one-slot {@code ItemContainerContents}: a raw {@code ItemStack}
     * is mutable and has no value equality, which NeoForge rejects the first time the component is set.
     */
    public static GolemComponent<ItemStack> itemStack(String namespace, String name) {
        //? if >=1.21.1 {
        return new GolemComponent<>(namespace, name,
                () -> net.minecraft.world.item.component.ItemContainerContents.CODEC,
                stack -> net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(stack)),
                contents -> contents.copyOne());
        //?} else {
        /*return new GolemComponent<>(namespace, name, new LegacyValue<>() {
            @Override
            public CompoundTag write(ItemStack value) {
                CompoundTag tag = new CompoundTag();
                value.save(tag);
                return tag;
            }

            @Override
            public ItemStack read(CompoundTag tag) {
                return ItemStack.of(tag);
            }
        });
        *///?}
    }

    /** Codec for fixed-slot stack lists; empty slots use the platform's optional stack representation. */
    public static Codec<java.util.List<ItemStack>> itemStackListCodec() {
        //? if >=1.21.1 {
        return ItemStack.OPTIONAL_CODEC.listOf();
        //?} else {
        /*return ItemStack.CODEC.listOf();
        *///?}
    }

    /** Returns the value, or empty when this stack has no valid value for this component. */
    public Optional<T> get(ItemStack stack) {
        Objects.requireNonNull(stack);
        //? if >=1.21.1 {
        Object stored = stack.get(component.get());
        return stored == null ? Optional.empty() : Optional.of(fromStored.apply(stored));
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key)) return Optional.empty();
        if (legacy != null) {
            return tag.contains(key, 10) ? Optional.of(legacy.read(tag.getCompound(key))) : Optional.empty();
        }
        return codec.get().parse(NbtOps.INSTANCE, tag.get(key))
                .resultOrPartial(error -> GolemCore.LOG.error("Failed to decode item component '{}': {}", key, error));
        *///?}
    }

    /** Returns the stored value, or {@code defaultValue} when absent or malformed. */
    public T getOr(ItemStack stack, T defaultValue) {
        return get(stack).orElse(defaultValue);
    }

    /** Stores a non-null value. */
    public void set(ItemStack stack, T value) {
        Objects.requireNonNull(stack);
        Objects.requireNonNull(value);
        //? if >=1.21.1 {
        stack.set(component.get(), toStored.apply(value));
        //?} else {
        /*if (legacy != null) {
            stack.getOrCreateTag().put(key, legacy.write(value));
            return;
        }
        codec.get().encodeStart(NbtOps.INSTANCE, value)
                .resultOrPartial(error -> GolemCore.LOG.error("Failed to encode item component '{}': {}", key, error))
                .ifPresent(encoded -> stack.getOrCreateTag().put(key, encoded));
        *///?}
    }

    public boolean has(ItemStack stack) {
        Objects.requireNonNull(stack);
        //? if >=1.21.1 {
        return stack.has(component.get());
        //?} else {
        /*return stack.hasTag() && stack.getTag().contains(key);
        *///?}
    }

    /** Removes this component and returns its previous value, if valid. */
    public Optional<T> remove(ItemStack stack) {
        Optional<T> previous = get(stack);
        //? if >=1.21.1 {
        stack.remove(component.get());
        //?} else {
        /*if (stack.hasTag()) stack.getTag().remove(key);
        *///?}
        return previous;
    }

    /** Replaces the current value with {@code operation.apply(getOr(stack, defaultValue))}. */
    public T update(ItemStack stack, T defaultValue, UnaryOperator<T> operation) {
        T value = Objects.requireNonNull(operation).apply(getOr(stack, defaultValue));
        set(stack, value);
        return value;
    }

    //? if <1.21.1 {
    /*private interface LegacyValue<T> {
        CompoundTag write(T value);

        T read(CompoundTag tag);
    }
    *///?}
}
