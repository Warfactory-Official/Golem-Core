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
 * instances of this class. Each entry is stored as a deferred factory. Golem Core fills the registries itself, once,
 * from every mod's registers ({@link dev.golem.core.platform.ModEntries}): per {@code RegisterEvent} through
 * {@link #flushRegistry} on NeoForge and Forge, all at once through {@link #flushAllToVanilla} on Fabric. A mod never
 * flushes; a register is flushed at most once, and an entry added after its register was flushed is reported, since it
 * will never exist.
 *
 * <p>Every entry's id is set on its {@link Item.Properties}/{@link BlockBehaviour.Properties} before
 * construction, as required by 26.1.
 */
public class DeferredRegister<T> {

    // Guarded by itself: NeoForge and Forge may construct mods, and so create their registers, in parallel.
    private static final List<DeferredRegister<?>> ALL = new ArrayList<>();

    /**
     * Registries flushed first, in this order, before any others. Blocks must come before items so
     * that {@code BlockItem} factories can resolve their block during flush. A method, not a constant:
     * naming a registry key runs the Registries initialiser, which on 1.20.1 needs a bootstrapped
     * game, and this class has to load in a plain JVM (see {@link RegistryObject}).
     */
    private static List<ResourceKey<? extends Registry<?>>> flushOrder() {
        return List.of(
            // Sound events before blocks, as vanilla creates the registries and as NeoForge/Forge fire their
            // RegisterEvents: a block whose BlockSetType or SoundType reads a mod sound event in its factory
            // (NTM: Space's fence gate) found it unregistered on Fabric only.
            Registries.SOUND_EVENT,
            // Fluids before blocks and items, as vanilla (and NeoForge) register them: a world fluid's bucket
            // takes its fluid at construction (dev.golem.core.fluid.WorldFluids).
            Registries.FLUID,
            Registries.BLOCK,
            //? if >=1.20.5 && <26.1 {
            /*// Pre-26.1 armour items take a Holder<ArmorMaterial> at construction, so the materials
            // must already be registered by the time item factories run.
            Registries.ARMOR_MATERIAL,
            *///?}
            // Entity types before items, as vanilla bootstraps them: a spawn egg resolves its entity type when the
            // item is constructed (see dev.golem.core.world.SpawnEggs).
            Registries.ENTITY_TYPE,
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
    private volatile boolean flushed;

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this(constant(Objects.requireNonNull(registryKey)), namespace);
    }

    /** As {@link #DeferredRegister(ResourceKey, String)}, naming the registry only when first needed. */
    protected DeferredRegister(Supplier<? extends ResourceKey<? extends Registry<T>>> registryKey, String namespace) {
        this.registryKey = lazy(Objects.requireNonNull(registryKey));
        this.namespace = Objects.requireNonNull(namespace);
        synchronized (ALL) {
            ALL.add(this);
        }
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
     * lazily, and its kind is known from its class, so a registrar can pick it among several registers without
     * resolving a registry key and the registration class still initialises in a plain JVM (see
     * {@link RegistryObject}).
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
        if (flushed) {
            dev.golem.core.GolemCore.LOG.error("{}:{} was registered after its registry was filled; it will not exist",
                    namespace, name);
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

    /** The namespace every entry of this register is registered under. */
    public String namespace() {
        return namespace;
    }

    /** Push every entry of this register into the given sink; a second flush of the same register does nothing. */
    public void flushTo(RegistrationSink sink) {
        if (flushed) {
            dev.golem.core.GolemCore.LOG.warn("A {} register for {} was flushed twice; the second flush was ignored",
                    dev.golem.core.Ids.keyId(getRegistryKey()), namespace);
            return;
        }
        flushed = true;
        ResourceKey<? extends Registry<T>> registry = getRegistryKey();
        String registryPath = dev.golem.core.Ids.keyId(registry).getPath();
        entries.forEach((name, original) -> {
            ResourceKey<T> key = keys.get(name).get();
            Function<ResourceKey<T>, ? extends T> factory =
                    Substitutions.resolve(Substitutions.ENTRY + registryPath, namespace + ":" + name, original);
            sink.accept(registry, key, () -> factory.apply(key));
        });
    }

    // --- static flush helpers used by the loader bootstraps ---

    /** A snapshot of every register any mod has created. */
    public static List<DeferredRegister<?>> all() {
        synchronized (ALL) {
            return Collections.unmodifiableList(new ArrayList<>(ALL));
        }
    }

    /**
     * Pushes every mod's registers for {@code registry} into {@code sink}: Golem Core's {@code RegisterEvent}
     * listener on NeoForge and Forge, which fires once per registry after every mod is constructed.
     */
    public static void flushRegistry(ResourceKey<? extends Registry<?>> registry, RegistrationSink sink) {
        for (DeferredRegister<?> register : all()) {
            if (register.getRegistryKey().equals(registry)) register.flushTo(sink);
        }
    }

    /**
     * Flushes every register straight into the vanilla registries, honouring {@link #flushOrder()} so
     * dependencies (blocks before items) are registered first. Golem Core calls this once on Fabric, which
     * registers everything synchronously at init time, after every mod's declarations.
     *
     * <p>The root-registry lookup differs by version: 26.1 looks up by {@code ResourceKey} and throws when absent,
     * older versions look up by location and return the value. That stays here, not in consumers.
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
        List<DeferredRegister<?>> remaining = new ArrayList<>(all());
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
            // The factory is resolved as the entry is built, so a substitution declared before then is honoured.
            String id = namespace + ":" + name;
            //? if >=26.1 {
            return register(name, key -> Substitutions.resolve(Substitutions.ITEM, id, factory)
                    .apply(properties.apply(new Item.Properties()).setId(key)));
            //?} else {
            /*return register(name, key -> Substitutions.resolve(Substitutions.ITEM, id, factory)
                    .apply(properties.apply(new Item.Properties())));
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
            // The item's translation key must be "block.<ns>.<name>" rather than "item.<ns>.<name>": 26.1 made
            // item description ids explicit and needs useBlockDescriptionPrefix(); before 26.1 BlockItem defers
            // to its block, which is the same key only while the item is named like its block, so
            // Compat.blockDescription marks the properties for CompatBlockItem to answer 26.1's key.
            //? if >=26.1 {
            return registerItem(name, props -> new dev.golem.core.world.CompatBlockItem(block.get(), props),
                    p -> properties.apply(p).useBlockDescriptionPrefix());
            //?} else {
            /*return registerItem(name, props -> new dev.golem.core.world.CompatBlockItem(block.get(), props),
                    p -> dev.golem.core.world.Compat.blockDescription(properties.apply(p)));
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
            String id = namespace + ":" + name;
            //? if >=26.1 {
            return register(name, key -> Substitutions.resolve(Substitutions.BLOCK, id, factory)
                    .apply(properties.apply(BlockBehaviour.Properties.of()).setId(key)));
            //?} else {
            /*return register(name, key -> Substitutions.resolve(Substitutions.BLOCK, id, factory)
                    .apply(properties.apply(BlockBehaviour.Properties.of())));
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
