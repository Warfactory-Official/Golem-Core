package dev.golem.core.registry;

import java.util.ArrayList;
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
 * instances of this class. Each entry is stored as a deferred factory; Golem Core's bootstrap flushes every
 * register of every mod once: per {@code RegisterEvent} on Forge-like loaders, after all Golem mods'
 * {@code init} on Fabric. Registering into a flushed register throws; after the flush use {@link #late}.
 *
 * <p>Every entry's id is set on its {@link Item.Properties}/{@link BlockBehaviour.Properties} before
 * construction, as required by 26.1.
 */
public class DeferredRegister<T> {

    private static final List<DeferredRegister<?>> ALL = new ArrayList<>();

    /**
     * Registries flushed first, in this order, before any others. Blocks must come before items so
     * that {@code BlockItem} factories can resolve their block during flush.
     */
    private static final List<ResourceKey<? extends Registry<?>>> FLUSH_ORDER = List.of(
            Registries.BLOCK,
            //? if >=1.20.5 && <26.1 {
            /*// Pre-26.1 armour items take a Holder<ArmorMaterial> at construction, so the materials
            // must already be registered by the time item factories run.
            Registries.ARMOR_MATERIAL,
            *///?}
            Registries.ITEM);

    protected final ResourceKey<? extends Registry<T>> registryKey;
    protected final String namespace;
    private final Map<ResourceKey<T>, Function<ResourceKey<T>, ? extends T>> entries = new LinkedHashMap<>();
    private boolean flushed;

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.namespace = Objects.requireNonNull(namespace);
        ALL.add(this);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        return new DeferredRegister<>(registryKey, namespace);
    }

    public static Items createItems(String namespace) {
        return new Items(namespace);
    }

    public static Blocks createBlocks(String namespace) {
        return new Blocks(namespace);
    }

    /**
     * Register an entry. The factory receives the entry's {@link ResourceKey} (useful for setting the
     * id on properties) and must return a <em>new</em> instance.
     */
    public <I extends T> RegistryObject<I> register(String name, Function<ResourceKey<T>, ? extends I> factory) {
        var id = dev.golem.core.Ids.of(namespace, name);
        if (flushed) throw new IllegalStateException("Register " + registryKey + " of " + namespace + " already flushed: " + id);
        ResourceKey<T> key = ResourceKey.create(registryKey, id);
        if (entries.putIfAbsent(key, factory) != null) {
            throw new IllegalArgumentException("Duplicate registration: " + id);
        }
        return new RegistryObject<>(registryKey, key);
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> supplier) {
        return register(name, key -> supplier.get());
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return registryKey;
    }

    private void flushTo(RegistrationSink sink) {
        if (flushed) throw new IllegalStateException("Register " + registryKey + " of " + namespace + " already flushed");
        flushed = true;
        entries.forEach((key, factory) -> sink.accept(registryKey, key, () -> factory.apply(key)));
    }

    /** Every unflushed register of {@code registry}, e.g. from one {@code RegisterEvent}. */
    public static synchronized void flushPending(ResourceKey<? extends Registry<?>> registry, RegistrationSink sink) {
        for (DeferredRegister<?> register : new ArrayList<>(ALL)) {
            if (!register.flushed && register.registryKey.equals(registry)) register.flushTo(sink);
        }
    }

    /**
     * Every unflushed register straight into the vanilla registries: the loader with no deferred-registration
     * phase (Fabric). 26.1 looks the root registry up by {@code ResourceKey}, older versions by location.
     */
    public static void flushPendingToVanilla() {
        flushPending(DeferredRegister::registerToVanilla);
        vanillaFlushed = true;
    }

    private static boolean vanillaFlushed;

    /**
     * {@code own} while unflushed; after the Fabric bootstrap flush, a fresh register of the same registry and namespace,
     * flushed by {@link #flushLate} (content phases do this after each late handler). Forge-like registries are frozen
     * after their {@code RegisterEvent}: throws.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T, R extends DeferredRegister<T>> R late(R own) {
        DeferredRegister<T> base = own;
        if (!base.flushed) return own;
        if (!vanillaFlushed) throw new IllegalStateException("Registry " + base.registryKey + " is frozen; register in init");
        if (base instanceof Items) return (R) new Items(base.namespace);
        if (base instanceof Blocks) return (R) new Blocks(base.namespace);
        return (R) new DeferredRegister<>(base.registryKey, base.namespace);
    }

    /** Every register created by {@link #late}, into vanilla. No-op before the Fabric bootstrap flush. */
    public static synchronized void flushLate() {
        if (vanillaFlushed) flushPending(DeferredRegister::registerToVanilla);
    }

    /** This register alone, straight into its vanilla registry: one created after the bootstrap flush. */
    public void flushToVanilla() {
        flushTo(DeferredRegister::registerToVanilla);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerToVanilla(ResourceKey<? extends Registry<?>> registryKey, ResourceKey<?> entryKey,
            Supplier<?> value) {
        //? if >=26.1 {
        Registry<Object> registry = (Registry<Object>) (Registry<?>)
                BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) registryKey);
        //?} else {
        /*Registry<Object> registry = (Registry<Object>) (Registry<?>)
                BuiltInRegistries.REGISTRY.get(registryKey.location());
        *///?}
        Registry.register(registry, (ResourceKey<Object>) (ResourceKey<?>) entryKey, value.get());
    }

    private static synchronized void flushPending(RegistrationSink sink) {
        List<DeferredRegister<?>> remaining = new ArrayList<>(ALL);
        remaining.removeIf(register -> register.flushed);
        for (ResourceKey<? extends Registry<?>> order : FLUSH_ORDER) {
            remaining.removeIf(dr -> {
                if (dr.registryKey.equals(order)) {
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
            super(Registries.ITEM, namespace);
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
            super(Registries.BLOCK, namespace);
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
}
