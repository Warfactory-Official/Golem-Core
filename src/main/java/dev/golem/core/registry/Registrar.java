package dev.golem.core.registry;

import dev.golem.core.Ids;
import dev.golem.core.dejson.Dejson;
import dev.golem.core.dejson.Tags;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.world.level.ItemLike;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentMap;
//?}

/**
 * Registration and its declarations in one chain, for any mod (after ntm-next's {@code Reg}):
 *
 * <pre>{@code
 * private static final Registrar REG = Registrar.of(BLOCKS, ITEMS);
 *
 * public static final RegistryObject<MachineSiren> MACHINE_SIREN = REG.block("machine_siren", MachineSiren::new,
 *                 p -> p.strength(5.0F, 10.0F).sound(SoundType.METAL).requiresCorrectToolForDrops())
 *         .transform(cubeBottomTop)
 *         .loot(Loot.self())
 *         .tag(BlockTag.PICKAXE);
 * }</pre>
 *
 * <ul>
 *   <li><b>A registrar per registration class</b>, wrapping that class's own {@link DeferredRegister}s, all of one
 *       namespace. Registers flush in the order they were created and entries in the order they were added, so a
 *       class's registrations keep their registry order (network ids, recipe-viewer order, "first registered"
 *       fallbacks).
 *   <li><b>A {@link Handle} is a {@link RegistryObject}</b>, so a field keeps its declared type
 *       ({@code RegistryObject<MachineSiren>}).
 *   <li><b>Declarations go to Dejson</b>, at build time, for simple blocks and items only: model, blockstate, item
 *       model, loot, tag membership. Each declaring method registers its declaration on the spot, so each surface is
 *       stated once. Hand-tuned JSON stays an authored file the chain only points at.
 * </ul>
 *
 * <p><b>The plain-JVM rule.</b> A registration class with declarations runs its static initialiser in DejsonMain,
 * where Minecraft is on the classpath but never bootstrapped. Anything that needs a running game (a
 * {@code SoundType} constant, a registry lookup, an {@code Item.Properties}) stays inside the factory and properties
 * lambdas, which registration only stores.
 *
 * <p>Another mod can replace an entry registered here without touching this chain: see {@link Substitutions}.
 */
public class Registrar {

    private final List<DeferredRegister<?>> registers;
    private final String namespace;

    protected Registrar(DeferredRegister<?>... registers) {
        if (registers.length == 0) throw new IllegalArgumentException("a registrar needs at least one register");
        this.registers = List.of(registers);
        this.namespace = registers[0].namespace();
        for (DeferredRegister<?> register : registers) {
            if (!register.namespace().equals(namespace)) {
                throw new IllegalArgumentException("a registrar's registers share one namespace: "
                        + namespace + " and " + register.namespace());
            }
        }
    }

    /** A registrar over the calling class's own registers (blocks, items, block entities, ...), in any order. */
    public static Registrar of(DeferredRegister<?>... registers) {
        return new Registrar(registers);
    }

    /** The namespace every registration here is made under. */
    public String namespace() {
        return namespace;
    }

    /** {@code "<ns>:<block> -> #<tag>"} for every item tag declared on a BLOCK handle; see {@link Handle#tag(ItemTag)}. */
    private static final Set<String> BLOCK_ITEM_TAG_MEMBERS =
            Collections.synchronizedSet(new LinkedHashSet<>());

    /** The item-tag memberships declared on block handles, each assuming a same-id block item exists. */
    public static Set<String> blockItemTagMembers() {
        synchronized (BLOCK_ITEM_TAG_MEMBERS) {
            return Set.copyOf(BLOCK_ITEM_TAG_MEMBERS);
        }
    }

    // --- blocks ---

    public <B extends Block> Handle<B> block(String name, Function<BlockBehaviour.Properties, ? extends B> factory,
                                             UnaryOperator<BlockBehaviour.Properties> properties) {
        return new Handle<>(blocks().registerBlock(name, factory, properties), namespace, name, Kind.BLOCK);
    }

    public <B extends Block> Handle<B> block(String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
        return new Handle<>(blocks().registerBlock(name, factory), namespace, name, Kind.BLOCK);
    }

    /**
     * A plain {@link Block}. Named apart from {@code block(name, factory)} so that an implicitly typed lambda
     * ({@code REG.block("x", p -> new X(p))}) has exactly one candidate.
     */
    public Handle<Block> simpleBlock(String name, UnaryOperator<BlockBehaviour.Properties> properties) {
        return new Handle<>(blocks().registerSimpleBlock(name, properties), namespace, name, Kind.BLOCK);
    }

    // --- items ---

    public <I extends Item> Handle<I> item(String name, Function<Item.Properties, ? extends I> factory,
                                           UnaryOperator<Item.Properties> properties) {
        return new Handle<>(items().registerItem(name, factory, properties), namespace, name, Kind.ITEM);
    }

    public <I extends Item> Handle<I> item(String name, Function<Item.Properties, ? extends I> factory) {
        return new Handle<>(items().registerItem(name, factory), namespace, name, Kind.ITEM);
    }

    /** A plain item ({@code CompatItem}). Named apart from {@code item(name, factory)} as {@link #simpleBlock} is. */
    public Handle<Item> simpleItem(String name, UnaryOperator<Item.Properties> properties) {
        return new Handle<>(items().registerSimpleItem(name, properties), namespace, name, Kind.ITEM);
    }

    public Handle<Item> item(String name) {
        return new Handle<>(items().registerSimpleItem(name), namespace, name, Kind.ITEM);
    }

    /** The block's plain item, under the block's own name, with the block's description id on every node. */
    public Handle<BlockItem> blockItem(RegistryObject<? extends Block> block) {
        String name = path(block);
        return new Handle<>(items().registerBlockItem(name, block), namespace, name, Kind.ITEM);
    }

    public Handle<BlockItem> blockItem(RegistryObject<? extends Block> block, UnaryOperator<Item.Properties> properties) {
        String name = path(block);
        return new Handle<>(items().registerBlockItem(name, block, properties), namespace, name, Kind.ITEM);
    }

    /**
     * The block's item of a custom class, under the block's own name: exactly
     * {@code ITEMS.registerItem(name, p -> factory.apply(block.get(), p), properties)}.
     */
    public <B extends Block, I extends Item> Handle<I> blockItem(RegistryObject<B> block,
                                                                 BiFunction<? super B, Item.Properties, ? extends I> factory,
                                                                 UnaryOperator<Item.Properties> properties) {
        String name = path(block);
        return new Handle<>(items().registerItem(name, p -> factory.apply(block.get(), p), properties), namespace, name,
                Kind.ITEM);
    }

    // --- everything else ---

    public <E extends BlockEntity> Handle<BlockEntityType<E>> blockEntity(String name, Supplier<BlockEntityType<E>> type) {
        return registerOn(find(DeferredRegister.BlockEntityTypes.class, () -> Registries.BLOCK_ENTITY_TYPE), name, type);
    }

    /** An entity type; see {@link EntityHandle} for why it is not a {@link Handle}. */
    public <E extends Entity> EntityHandle<E> entity(String name, Supplier<EntityType<E>> type) {
        return entity(name, key -> type.get());
    }

    /** An entity type whose builder needs its own key ({@code Entities.build(builder, key)}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <E extends Entity> EntityHandle<E> entity(String name,
                                                     Function<ResourceKey<EntityType<?>>, EntityType<E>> type) {
        DeferredRegister register = find(DeferredRegister.EntityTypes.class, () -> Registries.ENTITY_TYPE);
        RegistryObject<EntityType<E>> object = register.register(name, type);
        return new EntityHandle<>(object, namespace, name);
    }

    public <M extends AbstractContainerMenu> Handle<MenuType<M>> menu(String name, Supplier<MenuType<M>> type) {
        return registerOn(find(DeferredRegister.MenuTypes.class, () -> Registries.MENU), name, type);
    }

    /** A sound event {@code <ns>:<name>} with variable range. */
    public Handle<SoundEvent> sound(String name) {
        return registerOn(find(DeferredRegister.Sounds.class, () -> Registries.SOUND_EVENT), name,
                () -> SoundEvent.createVariableRangeEvent(Ids.of(namespace, name)));
    }

    /** Any other registry this registrar holds a register for (effects, features, particle types, ...). */
    public <T, R extends T> Handle<R> register(ResourceKey<? extends Registry<T>> registry, String name,
                                               Supplier<? extends R> value) {
        return registerOn(find(registry), name, value);
    }

    //? if >=1.20.5 {
    /**
     * Patches {@code item}'s default components when the loader builds them (a remainder pointing at another
     * mod's item, which cannot be named while this registrar's own properties are built). Queued during
     * declarations; each loader applies the queue once. The handle is resolved when the event fires, not here.
     * Call during mod construction.
     */
    public void modifyDefaultComponents(Supplier<? extends ItemLike> item,
                                        Consumer<DataComponentMap.Builder> patch) {
        ComponentPatches.add(item, patch);
    }
    //?}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <R> Handle<R> registerOn(DeferredRegister register, String name, Supplier<? extends R> value) {
        RegistryObject<R> object = register.register(name, value);
        return new Handle<>(object, namespace, name, Kind.OTHER);
    }

    // --- internals ---

    private DeferredRegister.Blocks blocks() {
        for (DeferredRegister<?> register : registers) {
            if (register instanceof DeferredRegister.Blocks blocks) return blocks;
        }
        throw new IllegalStateException("this registrar was given no DeferredRegister.Blocks");
    }

    private DeferredRegister.Items items() {
        for (DeferredRegister<?> register : registers) {
            if (register instanceof DeferredRegister.Items items) return items;
        }
        throw new IllegalStateException("this registrar was given no DeferredRegister.Items");
    }

    private DeferredRegister<?> find(ResourceKey<? extends Registry<?>> registry) {
        for (DeferredRegister<?> register : registers) {
            if (register.getRegistryKey().equals(registry)) return register;
        }
        throw new IllegalStateException("this registrar was given no register for " + registry);
    }

    /**
     * The register of one kind, found WITHOUT naming any registry when the class holds the typed register
     * ({@code DeferredRegister.createBlockEntityTypes/createMenuTypes/createSounds/createEntityTypes}):
     * {@code Registries.X} is only read to tell apart registers made with {@code DeferredRegister.create(...)}. On
     * 1.20.1 naming a registry key outside a bootstrapped game fails, so a class DejsonMain initialises uses the
     * typed registers.
     */
    @SuppressWarnings("rawtypes")
    private DeferredRegister<?> find(Class<? extends DeferredRegister> kind,
                                     Supplier<? extends ResourceKey<? extends Registry<?>>> registry) {
        for (DeferredRegister<?> register : registers) {
            if (kind.isInstance(register)) return register;
        }
        ResourceKey<? extends Registry<?>> key = registry.get();
        for (DeferredRegister<?> register : registers) {
            if (!typed(register) && register.getRegistryKey().equals(key)) return register;
        }
        throw new IllegalStateException("this registrar was given no register for " + key);
    }

    /** A register whose registry is known from its class, so it can never be the one a key search wants. */
    private static boolean typed(DeferredRegister<?> register) {
        return register instanceof DeferredRegister.Blocks || register instanceof DeferredRegister.Items
                || register instanceof DeferredRegister.BlockEntityTypes || register instanceof DeferredRegister.MenuTypes
                || register instanceof DeferredRegister.Sounds || register instanceof DeferredRegister.EntityTypes;
    }

    private static String path(RegistryObject<?> object) {
        return object instanceof Handle<?> handle ? handle.path : object.getId().getPath();
    }

    enum Kind { BLOCK, ITEM, OTHER }

    /**
     * A registration plus the declarations stated on it. It IS the {@link RegistryObject} the field holds:
     * {@code get()}, {@code getId()} and {@code getKey()} behave as on any registry object.
     *
     * <p>Every declaring method states one surface for this id and registers it at once with Dejson; stating a
     * surface twice fails there ("declared twice").
     */
    public static final class Handle<T> extends RegistryObject<T> {

        private final String namespace;
        private final String path;
        private final Kind kind;

        private Handle(RegistryObject<T> object, String namespace, String path, Kind kind) {
            super(object);
            this.namespace = namespace;
            this.path = path;
            this.kind = kind;
        }

        /** The registry path, e.g. {@code machine_siren}. */
        public String path() {
            return path;
        }

        /** The namespace of the registrar that made it. */
        public String namespace() {
            return namespace;
        }

        /** {@code <ns>:<path>}, as tags and loot name it. */
        public String id() {
            return namespace + ":" + path;
        }

        /**
         * The block's model, blockstate and (optionally) its block item's model, as one Dejson block declaration:
         * {@code .model(d -> d.models(Models.cubeAll(...)).states(Variants.simple()).blockItemDirect())}.
         */
        public Handle<T> model(UnaryOperator<Dejson.Declaration> declaration) {
            require(Kind.BLOCK, "model");
            declaration.apply(Dejson.block(namespace, path)).register();
            return this;
        }

        /**
         * The item's model and definition as one Dejson item declaration. Valid on a block too, for a block item
         * declared apart from its block (same id).
         */
        public Handle<T> icon(UnaryOperator<Dejson.ItemDeclaration> declaration) {
            if (kind == Kind.OTHER) throw new IllegalStateException(id() + " is neither a block nor an item");
            declaration.apply(Dejson.item(namespace, path)).register();
            return this;
        }

        /** The block's loot table ({@code data/<ns>/loot_table/blocks/<path>.json}). */
        public Handle<T> loot(Loot loot) {
            require(Kind.BLOCK, "loot");
            loot.declare(namespace, path);
            return this;
        }

        /** Makes this BLOCK a member of a block tag; memberships accumulate across every mod. */
        public Handle<T> tag(BlockTag tag) {
            require(Kind.BLOCK, "a block tag");
            Tags.member("block", tag.namespace(), tag.path(), id());
            return this;
        }

        /**
         * Makes this registration's ITEM (a block's item shares its id) a member of an item tag.
         *
         * <p>On a BLOCK handle this assumes the block has an item of the same id, which the declaration cannot see.
         * An item-less block here would write an unknown required entry, and vanilla then drops the WHOLE item tag
         * file without a word, so every such id is recorded in {@link #blockItemTagMembers()} for a check to resolve.
         */
        public Handle<T> tag(ItemTag tag) {
            if (kind == Kind.OTHER) throw new IllegalStateException(id() + " is neither a block nor an item");
            if (kind == Kind.BLOCK) BLOCK_ITEM_TAG_MEMBERS.add(id() + " -> #" + tag.namespace() + ":" + tag.path());
            Tags.member("item", tag.namespace(), tag.path(), id());
            return this;
        }

        /** Lists this item in a creative tab, in call order. */
        @SuppressWarnings("unchecked")
        public Handle<T> tab(TabTarget tab) {
            require(Kind.ITEM, "a creative tab");
            tab.list((RegistryObject<? extends Item>) (RegistryObject<?>) this);
            return this;
        }

        /** Applies a reusable bundle of declarations. */
        public Handle<T> transform(Preset preset) {
            preset.applyTo(this);
            return this;
        }

        /** Runs an action against the HANDLE now (e.g. to record it); the object is not registered yet. */
        public Handle<T> andThen(Consumer<? super Handle<T>> action) {
            action.accept(this);
            return this;
        }

        public Handle<T> addTo(Collection<? super Handle<T>> roster) {
            roster.add(this);
            return this;
        }

        public boolean isBlock() {
            return kind == Kind.BLOCK;
        }

        private void require(Kind expected, String what) {
            if (kind != expected) {
                throw new IllegalStateException(id() + " is a " + kind.name().toLowerCase(Locale.ROOT)
                        + " registration; " + what + " belongs on a " + expected.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    /**
     * A registered entity type, with the declarations an entity type can carry. Deliberately not a {@link Handle}:
     * one name can be a block and an entity at once, so the scope rides the type and a block-only statement cannot
     * be made on it. It IS the {@link RegistryObject} the field holds.
     */
    public static final class EntityHandle<E extends Entity> extends RegistryObject<EntityType<E>> {

        private final String namespace;
        private final String path;

        private EntityHandle(RegistryObject<EntityType<E>> object, String namespace, String path) {
            super(object);
            this.namespace = namespace;
            this.path = path;
        }

        /** The registry path, e.g. {@code entity_coin}. */
        public String path() {
            return path;
        }

        /** {@code <ns>:<path>}, as tags name it. */
        public String id() {
            return namespace + ":" + path;
        }

        /** Makes this entity type a member of an entity-type tag; memberships accumulate across every mod. */
        public EntityHandle<E> tag(EntityTag tag) {
            Tags.member("entity_type", tag.namespace(), tag.path(), id());
            return this;
        }

        public EntityHandle<E> addTo(Collection<? super EntityHandle<E>> roster) {
            roster.add(this);
            return this;
        }
    }
}
