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
public final class GolemComponent<T> {

    //? if >=1.21.1 {
    private static final Map<String, DeferredRegister<DataComponentType<?>>> REGISTERS = new HashMap<>();
    //?}

    private final String key;
    //? if >=1.21.1 {
    private final RegistryObject<DataComponentType<T>> component;
    //?} else {
    /*private final Codec<T> codec;
    private final LegacyValue<T> legacy;
    *///?}

    private GolemComponent(String namespace, String name, Codec<T> codec) {
        key = Ids.of(namespace, name).toString();
        //? if >=1.21.1 {
        DeferredRegister<DataComponentType<?>> register = REGISTERS.computeIfAbsent(namespace,
                ns -> DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ns));
        component = register.register(name, () -> componentType(codec));
        //?} else {
        /*this.codec = codec;
        legacy = null;
        *///?}
    }

    //? if >=1.21.1 {
    static <T> DataComponentType<T> componentType(Codec<T> codec) {
        return DataComponentType.<T>builder().persistent(codec).networkSynchronized(
                net.minecraft.network.codec.ByteBufCodecs.fromCodecWithRegistries(codec)).build();
    }
    //?}

    //? if <1.21.1 {
    /*private GolemComponent(String namespace, String name, LegacyValue<T> legacy) {
        key = Ids.of(namespace, name).toString();
        codec = null;
        this.legacy = legacy;
    }
    *///?}

    /** Creates a component backed by the supplied persistent codec. */
    public static <T> GolemComponent<T> of(String namespace, String name, Codec<T> codec) {
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
    /** Native component handle for integrations that explicitly consume vanilla component types. */
    public DataComponentType<T> type() {
        return component.get();
    }
    //?}

    /** A component holding one stack, including all of its item data. */
    public static GolemComponent<ItemStack> itemStack(String namespace, String name) {
        //? if >=1.21.1 {
        return of(namespace, name, ItemStack.CODEC);
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

    /** Returns the value, or empty when this stack has no valid value for this component. */
    public Optional<T> get(ItemStack stack) {
        Objects.requireNonNull(stack);
        //? if >=1.21.1 {
        return Optional.ofNullable(stack.get(component.get()));
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(key)) return Optional.empty();
        if (legacy != null) {
            return tag.contains(key, 10) ? Optional.of(legacy.read(tag.getCompound(key))) : Optional.empty();
        }
        return codec.parse(NbtOps.INSTANCE, tag.get(key))
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
        stack.set(component.get(), value);
        //?} else {
        /*if (legacy != null) {
            stack.getOrCreateTag().put(key, legacy.write(value));
            return;
        }
        codec.encodeStart(NbtOps.INSTANCE, value)
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
