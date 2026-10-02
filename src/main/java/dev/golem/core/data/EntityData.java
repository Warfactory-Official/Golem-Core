package dev.golem.core.data;

import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Typed per-entity state that is saved and loaded with the entity, on every loader.
 *
 * <p>Declare once, before content registration is flushed, and read it anywhere:
 * <pre>{@code
 * static final EntityData<Props> PROPS = EntityData.of("mymod", "props", LivingEntity.class,
 *         Props::new, Props::save, Props::load);
 * Props props = PROPS.get(entity);   // created on first access, never null
 * }</pre>
 *
 * <p>Each loader keeps the value in its own native per-entity storage, so persistence rides the
 * loader's own entity save path rather than a mod mixin on vanilla's save methods:
 * <ul>
 * <li><b>NeoForge</b> (26.1, 1.21.1): a data attachment, registered through the ordinary
 * {@link dev.golem.core.registry.DeferredRegister} queue (saved under {@code neoforge:attachments}).</li>
 * <li><b>Fabric</b> (every node): a persistent {@code fabric-data-attachment-api-v1} attachment (saved under
 * {@code fabric:attachments}).</li>
 * <li><b>Forge 1.20.1</b>, which has no attachments: a serializable capability provider attached in
 * {@code AttachCapabilitiesEvent} (saved under {@code ForgeCaps."golemcore:entity_data"}). The live value is
 * reached through an identity map rather than {@code getCapability}, because Forge invalidates a removed
 * entity's capabilities — the original player at a respawn copy among them — and the attachment loaders do
 * not. A value must therefore never hold a strong reference to its entity.</li>
 * </ul>
 *
 * <p><b>Copy semantics are the same everywhere: the loader never copies the value to a new entity</b> for a
 * player respawn, a return from the End or a mob conversion. What survives is the consumer's decision, made
 * in {@link dev.golem.core.platform.Lifecycle#onPlayerCopy}. (NeoForge is given a copy handler that declines;
 * Fabric's own transfer is undone before the {@code onPlayerCopy} handlers run.) A non-player entity that
 * changes dimension is re-created from its own save data by vanilla, so its value travels with it.
 *
 * <p>{@link #migrateFrom} reads a mod's pre-attachment save format once: an entity that loads with that child
 * tag at the root of its save data and no stored value gets the child read into its value; the old child is
 * then simply not written again.
 */
public final class EntityData<T> {

    /** Writes a value into its own compound. */
    @FunctionalInterface
    public interface Writer<T> {
        void save(T value, DataOut out);
    }

    /** Reads saved data into a freshly created value. */
    @FunctionalInterface
    public interface Reader<T> {
        void load(T value, DataIn in);
    }

    private static final List<EntityData<?>> ALL = new CopyOnWriteArrayList<>();
    private static final AtomicInteger MIGRATED = new AtomicInteger();
    private static volatile boolean anyLegacy;

    private final String key;
    private final Class<? extends Entity> holder;
    private final Supplier<T> factory;
    private final Writer<T> writer;
    private final Reader<T> reader;
    private volatile @Nullable String legacyKey;

    //? if neoforge {
    private static final java.util.Map<String, dev.golem.core.registry.DeferredRegister<
            net.neoforged.neoforge.attachment.AttachmentType<?>>> REGISTERS = new java.util.HashMap<>();
    private final net.neoforged.neoforge.attachment.AttachmentType<T> type;
    //?} elif fabric {
    /*private final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Slot<T>> type;
    *///?} elif forge {
    /*private final java.util.concurrent.ConcurrentMap<Entity, T> values =
            new com.google.common.collect.MapMaker().weakKeys().makeMap();
    *///?}

    private EntityData(String namespace, String name, Class<? extends Entity> holder, Supplier<T> factory,
                       Writer<T> writer, Reader<T> reader) {
        this.key = Ids.of(namespace, name).toString();
        this.holder = Objects.requireNonNull(holder);
        this.factory = Objects.requireNonNull(factory);
        this.writer = Objects.requireNonNull(writer);
        this.reader = Objects.requireNonNull(reader);
        //? if neoforge {
        type = net.neoforged.neoforge.attachment.AttachmentType.builder(factory)
                .serialize(serializer())
                // A declining copy handler: NeoForge would otherwise copy every serialized attachment to the
                // new player on an End return, which Fabric's reset and Forge's capabilities do not.
                .copyHandler((value, target, provider) -> null)
                .build();
        synchronized (REGISTERS) {
            REGISTERS.computeIfAbsent(namespace, ns -> dev.golem.core.registry.DeferredRegister.create(
                    net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ns))
                    .register(name, () -> type);
        }
        //?} elif fabric {
        /*type = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Slot<T>>builder()
                .initializer(Slot::new)
                .persistent(CompoundTag.CODEC.xmap(Slot::<T>pending, this::encode))
                .buildAndRegister(Ids.of(namespace, name));
        installFabric();
        *///?} elif forge {
        /*installForge();
        *///?}
    }

    /**
     * Declares a value carried by every entity of {@code holder}'s class (and its subclasses).
     *
     * @param factory creates the default value; called for the first access and before every load
     * @param writer  saves a value into its own compound
     * @param reader  loads saved data into a value the factory just created
     */
    public static <T> EntityData<T> of(String namespace, String name, Class<? extends Entity> holder,
                                       Supplier<T> factory, Writer<T> writer, Reader<T> reader) {
        EntityData<T> data = new EntityData<>(namespace, name, holder, factory, writer, reader);
        ALL.add(data);
        return data;
    }

    /**
     * Reads the mod's previous save format once: when an entity loads with a compound named {@code legacyKey}
     * at the root of its save data and has no stored value, that compound is read into the value. Nothing
     * writes the old compound again, so it is gone after the entity's next save.
     */
    public EntityData<T> migrateFrom(String legacyKey) {
        this.legacyKey = Objects.requireNonNull(legacyKey);
        anyLegacy = true;
        return this;
    }

    /** {@code namespace:name}; also the key the value is stored under. */
    public String key() {
        return key;
    }

    /** The entity's value, created from the factory on first access. Never null. */
    public T get(Entity entity) {
        //? if neoforge {
        return entity.getData(type);
        //?} elif fabric {
        /*Slot<T> slot = entity.getAttachedOrCreate(type);
        T value = slot.value;
        return value != null ? value : materialize(slot, entity);
        *///?} elif forge {
        /*T value = values.get(entity);
        return value != null ? value : values.computeIfAbsent(entity, e -> factory.get());
        *///?}
    }

    /** Whether the entity has a value yet — loaded from its save data, or created by {@link #get}. */
    public boolean has(Entity entity) {
        //? if neoforge {
        return entity.hasData(type);
        //?} elif fabric {
        /*return entity.hasAttached(type);
        *///?} elif forge {
        /*return values.containsKey(entity);
        *///?}
    }

    /** True while any declaration has a legacy key, so the load hook can skip all work otherwise. */
    public static boolean migrating() {
        return anyLegacy;
    }

    /**
     * Load hook, called once an entity has finished loading its save data (Golem Core's
     * {@code MixinEntityDataMigration}); not for consumers.
     */
    public static void migrateLegacy(Entity entity, DataIn data) {
        for (EntityData<?> declared : ALL) declared.migrate(entity, data);
    }

    private void migrate(Entity entity, DataIn data) {
        String legacy = legacyKey;
        if (legacy == null || !holder.isInstance(entity)) return;
        Optional<DataIn> old = data.child(legacy);
        if (old.isEmpty() || has(entity)) return;
        reader.load(get(entity), old.get());
        int count = MIGRATED.incrementAndGet();
        if (count == 1) {
            GolemCore.LOG.info("Migrated legacy '{}' entity data into {} (first: {}); further migrations log at debug",
                    legacy, key, entity.getType());
        } else {
            GolemCore.LOG.debug("Migrated legacy '{}' entity data into {} ({} so far)", legacy, key, count);
        }
    }

    // ---- CompoundTag bridge for the backends that store a plain compound (Fabric, Forge) ----

    //? if >=26.1 {
    private static DataIn dataIn(CompoundTag tag, HolderLookup.Provider registries) {
        return new ValueDataIn(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, tag));
    }

    private CompoundTag writeTag(T value, HolderLookup.@Nullable Provider registries) {
        net.minecraft.world.level.storage.TagValueOutput output = registries != null
                ? net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING, registries)
                : net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(
                        net.minecraft.util.ProblemReporter.DISCARDING);
        writer.save(value, new ValueDataOut(output));
        return output.buildResult();
    }
    //?} else {
    /*private static DataIn dataIn(CompoundTag tag, HolderLookup.Provider registries) {
        return new TagDataIn(tag, registries);
    }

    private CompoundTag writeTag(T value, HolderLookup.@Nullable Provider registries) {
        CompoundTag tag = new CompoundTag();
        writer.save(value, new TagDataOut(tag, registries));
        return tag;
    }
    *///?}

    // ---- NeoForge ----

    //? if neoforge && >=26.1 {
    private net.neoforged.neoforge.attachment.IAttachmentSerializer<T> serializer() {
        return new net.neoforged.neoforge.attachment.IAttachmentSerializer<>() {
            @Override
            public T read(net.neoforged.neoforge.attachment.IAttachmentHolder target,
                          net.minecraft.world.level.storage.ValueInput input) {
                T value = factory.get();
                reader.load(value, new ValueDataIn(input));
                return value;
            }

            @Override
            public boolean write(T value, net.minecraft.world.level.storage.ValueOutput output) {
                writer.save(value, new ValueDataOut(output));
                return true;
            }
        };
    }
    //?} elif neoforge {
    /*private net.neoforged.neoforge.attachment.IAttachmentSerializer<CompoundTag, T> serializer() {
        return new net.neoforged.neoforge.attachment.IAttachmentSerializer<>() {
            @Override
            public T read(net.neoforged.neoforge.attachment.IAttachmentHolder target, CompoundTag tag,
                          HolderLookup.Provider registries) {
                T value = factory.get();
                reader.load(value, new TagDataIn(tag, registries));
                return value;
            }

            @Override
            public CompoundTag write(T value, HolderLookup.Provider registries) {
                return writeTag(value, registries);
            }
        };
    }
    *///?}

    // ---- Fabric ----

    //? if fabric {
    /*static final class Slot<T> {
        // What Fabric stores: the persistence codec has no entity or registry context, so a loaded compound is
        // kept as-is and read into a value on first access, with the entity's registries.
        volatile @Nullable T value;
        @Nullable CompoundTag pending;
        HolderLookup.@Nullable Provider registries;

        static <T> Slot<T> pending(CompoundTag tag) {
            Slot<T> slot = new Slot<>();
            slot.pending = tag;
            return slot;
        }
    }

    private T materialize(Slot<T> slot, Entity entity) {
        synchronized (slot) {
            T value = slot.value;
            if (value == null) {
                HolderLookup.Provider registries = entity.level().registryAccess();
                value = factory.get();
                if (slot.pending != null) reader.load(value, dataIn(slot.pending, registries));
                slot.pending = null;
                slot.registries = registries;
                slot.value = value;
            }
            return value;
        }
    }

    private CompoundTag encode(Slot<T> slot) {
        synchronized (slot) {
            T value = slot.value;
            if (value == null) return slot.pending != null ? slot.pending : new CompoundTag();
            return writeTag(value, slot.registries);
        }
    }

    private static boolean fabricInstalled;

    /^*
     * Fabric transfers every attachment to the new player on an End return (and copy-on-death ones on a respawn)
     * in the default phase of COPY_FROM. This phase runs after it and discards the transferred values, before
     * Lifecycle's player-copy phase runs the consumers' own copies.
     ^/
    private static synchronized void installFabric() {
        if (fabricInstalled) return;
        fabricInstalled = true;
        var event = net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.COPY_FROM;
        event.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, Ids.of(GolemCore.MOD_ID, "entity_data"));
        event.addPhaseOrdering(Ids.of(GolemCore.MOD_ID, "entity_data"), Ids.of(GolemCore.MOD_ID, "player_copy"));
        event.register(Ids.of(GolemCore.MOD_ID, "entity_data"), (oldPlayer, newPlayer, alive) -> {
            for (EntityData<?> declared : ALL) declared.discardFabric(newPlayer);
        });
    }

    private void discardFabric(Entity entity) {
        entity.removeAttached(type);
    }
    *///?}

    // ---- Forge ----

    //? if forge {
    /*private static boolean forgeInstalled;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void installForge() {
        if (forgeInstalled) return;
        forgeInstalled = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(Entity.class,
                net.minecraftforge.eventbus.api.EventPriority.NORMAL, false,
                (Class) net.minecraftforge.event.AttachCapabilitiesEvent.class,
                event -> attachForge((net.minecraftforge.event.AttachCapabilitiesEvent<Entity>) event));
    }

    private static void attachForge(net.minecraftforge.event.AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        for (EntityData<?> declared : ALL) {
            if (declared.holder.isInstance(entity)) {
                event.addCapability(Ids.of(GolemCore.MOD_ID, "entity_data"), new ForgeStore(entity));
                return;
            }
        }
    }

    /^* Persistence only: every value lives in its declaration's identity map. ^/
    private static final class ForgeStore implements net.minecraftforge.common.capabilities.ICapabilitySerializable<CompoundTag> {
        private final Entity entity;

        ForgeStore(Entity entity) {
            this.entity = entity;
        }

        @Override
        public <C> net.minecraftforge.common.util.LazyOptional<C> getCapability(
                net.minecraftforge.common.capabilities.Capability<C> capability, net.minecraft.core.@Nullable Direction side) {
            return net.minecraftforge.common.util.LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            for (EntityData<?> declared : ALL) declared.saveForge(entity, tag);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            for (EntityData<?> declared : ALL) declared.loadForge(entity, tag);
        }
    }

    private void saveForge(Entity entity, CompoundTag tag) {
        T value = values.get(entity);
        if (value != null) tag.put(key, writeTag(value, entity.level().registryAccess()));
    }

    private void loadForge(Entity entity, CompoundTag tag) {
        if (!tag.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) return;
        T value = factory.get();
        reader.load(value, dataIn(tag.getCompound(key), entity.level().registryAccess()));
        values.put(entity, value);
    }
    *///?}
}
