package dev.golem.core.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Cross-loader registration helper. Mirrors the ergonomics of NeoForge's {@code DeferredRegister}
 * but is implemented purely against vanilla APIs so the same code compiles and runs on every loader.
 *
 * <p>Common content classes ({@code ModItems}, {@code ModBlocks}, ...) declare their entries against
 * instances of this class. Each entry is stored as a deferred factory; the actual registration is
 * driven later by the loader-specific bootstrap via {@link #flushTo(RegistrationSink)} (NeoForge,
 * per {@code RegisterEvent}) or {@link #flushAll(RegistrationSink)} (Fabric, during init).
 *
 * <p>Every entry's id is set on its {@link Item.Properties}/{@link BlockBehaviour.Properties} before
 * construction, as required by 26.1.
 */
public class DeferredRegister<T> {

    private static final List<DeferredRegister<?>> ALL = new ArrayList<>();

    /**
     * Registries flushed first, in this order, before any others. Blocks must come before items so
     * that {@code BlockItem} factories can resolve their block during flush. A method, not a constant:
     * naming a registry key runs the Registries initialiser, which on 1.20.1 needs a bootstrapped
     * game, and this class has to load in a plain JVM (see {@link RegistryObject}).
     */
    private static List<ResourceKey<? extends Registry<?>>> flushOrder() {
        return List.of(
            Registries.BLOCK,
            //? if >=1.20.5 && <26.1 {
            /*// Pre-26.1 armour items take a Holder<ArmorMaterial> at construction, so the materials
            // must already be registered by the time item factories run.
            Registries.ARMOR_MATERIAL,
            *///?}
            Registries.ITEM);
    }

    // The registry key and every entry key are created on first use rather than here, so that a
    // registration class can be initialised in a plain JVM on every node (Dejson runs registration
    // classes at build time to collect the declarations chained onto them). 26.1 creates keys freely
    // outside a game; 1.20.1 does not. In game the first use is the same as before: the first read of
    // a key, or the flush.
    private final Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey;
    protected final String namespace;
    private final Map<String, Function<ResourceKey<T>, ? extends T>> entries = new LinkedHashMap<>();
    private final Map<String, Supplier<ResourceKey<T>>> keys = new LinkedHashMap<>();

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this(constant(Objects.requireNonNull(registryKey)), namespace);
    }

    /** As {@link #DeferredRegister(ResourceKey, String)}, naming the registry only when first needed. */
    protected DeferredRegister(Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey, String namespace) {
        this.registryKey = lazy(Objects.requireNonNull(registryKey));
        this.namespace = Objects.requireNonNull(namespace);
        ALL.add(this);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    /**
     * A register whose registry key is named lazily, e.g. {@code create(() -> Registries.MENU, "hbm")}:
     * the form a registration class that must initialise in a plain JVM uses (see {@link RegistryObject}).
     */
    public static <T> DeferredRegister<T> create(Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey,
                                                 String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    private static <K> Supplier<K> constant(K value) {
        return () -> value;
    }

    /** Memoises {@code source}; registration is single-threaded, and a race would compute equal interned keys. */
    private static <K> Supplier<K> lazy(Supplier<? extends K> source) {
        return new Supplier<>() {
            private volatile K value;

            @Override
            public K get() {
                K resolved = value;
                if (resolved == null) {
                    value = resolved = Objects.requireNonNull(source.get());
                }
                return resolved;
            }
        };
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    /**
     * A block-entity-type register. Like {@link #createBlocks}/{@link #createItems} its registry is named
     * lazily, and its kind is known from its class: a registrar choosing among several registers can pick
     * it without resolving any registry key, so a registration class using one still initialises in a
     * plain JVM on every node (see {@link RegistryObject}).
     */
    public static BlockEntityTypes createBlockEntityTypes(String namespace) {
        return new BlockEntityTypes(namespace);
    }

    /** A menu-type register; see {@link #createBlockEntityTypes}. */
    public static MenuTypes createMenuTypes(String namespace) {
        return new MenuTypes(namespace);
    }

    /** A sound-event register; see {@link #createBlockEntityTypes}. */
    public static Sounds createSounds(String namespace) {
        return new Sounds(namespace);
    }

    /** An entity-type register; see {@link #createBlockEntityTypes}. */
    public static EntityTypes createEntityTypes(String namespace) {
        return new EntityTypes(namespace);
    }

    /**
     * Register an entry. The factory receives the entry's {@link ResourceKey} (useful for setting the
     * id on properties) and must return a <em>new</em> instance.
     */
    public <I extends T> RegistryObject<I> register(String name, Function<ResourceKey<T>, ? extends I> factory) {
        if (entries.putIfAbsent(name, factory) != null) {
            throw new IllegalArgumentException("Duplicate registration: " + namespace + ":" + name);
        }
        Supplier<ResourceKey<T>> key = lazy(() -> ResourceKey.create(getRegistryKey(), dev.golem.core.Ids.of(namespace, name)));
        keys.put(name, key);
        return new RegistryObject<>(registryKey, key);
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        return register(name, key -> supplier.get());
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey.get();
    }

    /** Push every entry of this register into the given sink. */
    public void flushTo(RegistrationSink sink) {
        ResourceKey<? extends Registry<T>> registry = getRegistryKey();
        entries.forEach((name, factory) -> {
            ResourceKey<T> key = keys.get(name).get();
            sink.accept(registry, key, () -> factory.apply(key));
        });
    }

    // --- static flush helpers used by the loader bootstraps ---

    public static List<DeferredRegister<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    /**
     * Flushes every register straight into the vanilla registries, honouring {@link #flushOrder()} so
     * dependencies (blocks before items) are registered first. Used by Fabric, which registers everything
     * synchronously at init time.
     *
     * <p>This is what a loader with no deferred-registration phase (Fabric) wants, and writing it out
     * at the call site meant writing the root-registry lookup too — which is exactly the kind of thing
     * that changed: 26.1 looks up by {@code ResourceKey} and throws when absent, older versions look
     * up by location and return the value. That belongs here, not in every consumer's initializer.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void flushAllToVanilla() {
        flushAll((registryKey, entryKey, value) -> {
            //? if >=26.1 {
            Registry<Object> registry = (Registry<Object>) (Registry<?>)
                    BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registryKey);
            //?} else {
            /*Registry<Object> registry = (Registry<Object>) (Registry<?>)
                    BuiltInRegistries.REGISTRY.get(registryKey.location());
            *///?}
            Registry.register(registry, (ResourceKey<Object>) (ResourceKey<?>) entryKey, value.get());
        });
    }

    /** Pushes every register into {@code sink}, in {@link #flushOrder()} first and then declaration order. */
    public static void flushAll(RegistrationSink sink) {
        List<DeferredRegister<?>> remaining = new ArrayList<>(ALL);
        for (ResourceKey<? extends Registry<?>> order : flushOrder()) {
            remaining.removeIf(dr -> {
                if (dr.getRegistryKey().equals(order)) {
                    dr.flushTo(sink);
                    return true;
                }
                return false;
            });
        }
        remaining.forEach(dr -> dr.flushTo(sink));
    }

    // === specialized registers (NeoForge-style ergonomics, vanilla-only implementation) ===

    /** Specialized register for {@link Item}s. */
    public static class Items extends DeferredRegister<Item> {

        protected Items(String namespace) {
            super(() -> Registries.ITEM, namespace);
        }

        public <I extends Item> RegistryObject<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
            //? if >=26.1 {
            return register(name, key -> factory.apply(properties.apply(new Item.Properties()).setId(key)));
            //?} else {
            /*return register(name, key -> factory.apply(properties.apply(new Item.Properties())));
            *///?}
        }

        public <I extends Item> RegistryObject<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
            return registerItem(name, factory, UnaryOperator.identity());
        }

        public RegistryObject<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return registerItem(name, dev.golem.core.world.CompatItem::new, properties);
        }

        public RegistryObject<Item> registerSimpleItem(String name) {
            return registerSimpleItem(name, UnaryOperator.identity());
        }

        public RegistryObject<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block, UnaryOperator<Item.Properties> properties) {
            // The item's translation key must be "block.<ns>.<name>", matching the block, rather than
            // "item.<ns>.<name>". How that is achieved is version-dependent: 26.1 made item description
            // ids explicit and needs useBlockDescriptionPrefix(), whereas pre-26.1 BlockItem already
            // overrides getDescriptionId() to defer to its block, so no opt-in exists or is needed.
            //? if >=26.1 {
            return registerItem(name, props -> new dev.golem.core.world.CompatBlockItem(block.get(), props),
                    p -> properties.apply(p).useBlockDescriptionPrefix());
            //?} else {
            /*return registerItem(name, props -> new dev.golem.core.world.CompatBlockItem(block.get(), props), properties);
            *///?}
        }

        public RegistryObject<BlockItem> registerBlockItem(String name, Supplier<? extends Block> block) {
            return registerBlockItem(name, block, UnaryOperator.identity());
        }
    }

    /** Specialized register for {@link Block}s. */
    public static class Blocks extends DeferredRegister<Block> {

        protected Blocks(String namespace) {
            super(() -> Registries.BLOCK, namespace);
        }

        public <B extends Block> RegistryObject<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
            //? if >=26.1 {
            return register(name, key -> factory.apply(properties.apply(BlockBehaviour.Properties.of()).setId(key)));
            //?} else {
            /*return register(name, key -> factory.apply(properties.apply(BlockBehaviour.Properties.of())));
            *///?}
        }

        public <B extends Block> RegistryObject<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
            return registerBlock(name, factory, UnaryOperator.identity());
        }

        public RegistryObject<Block> registerSimpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
            return registerBlock(name, Block::new, properties);
        }

        public RegistryObject<Block> registerSimpleBlock(String name) {
            return registerSimpleBlock(name, UnaryOperator.identity());
        }
    }

    /** Register for block entity types, identifiable without naming its registry ({@link #createBlockEntityTypes}). */
    public static class BlockEntityTypes extends DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> {

        protected BlockEntityTypes(String namespace) {
            super(() -> Registries.BLOCK_ENTITY_TYPE, namespace);
        }
    }

    /** Register for menu types ({@link #createMenuTypes}). */
    public static class MenuTypes extends DeferredRegister<net.minecraft.world.inventory.MenuType<?>> {

        protected MenuTypes(String namespace) {
            super(() -> Registries.MENU, namespace);
        }
    }

    /** Register for sound events ({@link #createSounds}). */
    public static class Sounds extends DeferredRegister<net.minecraft.sounds.SoundEvent> {

        protected Sounds(String namespace) {
            super(() -> Registries.SOUND_EVENT, namespace);
        }
    }

    /** Register for entity types ({@link #createEntityTypes}). */
    public static class EntityTypes extends DeferredRegister<net.minecraft.world.entity.EntityType<?>> {

        protected EntityTypes(String namespace) {
            super(() -> Registries.ENTITY_TYPE, namespace);
        }
    }
}
