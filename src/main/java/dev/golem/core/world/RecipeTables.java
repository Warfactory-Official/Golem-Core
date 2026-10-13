package dev.golem.core.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import dev.golem.core.Ids;
import dev.golem.core.hook.Hook;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import dev.golem.core.platform.Lifecycle;
import dev.golem.core.registry.DeferredRegister;
import dev.golem.core.registry.RegistryObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
//?} else {
/*import net.minecraft.world.Container;
*///?}

/**
 * Data-driven recipe tables: a mod's machine recipes as ordinary datapack recipes of their own vanilla
 * {@link RecipeType}, one per machine, in {@code data/<ns>/recipe/...} (any subfolder) with {@code "type": "<ns>:<name>"}.
 * Datapacks, KubeJS and other mods add and replace them like any recipe, and remove rows of another mod's table by
 * listing their ids in {@code data/<own namespace>/recipe_removals/<path>.json} ({@code {"recipes": [ids]}}), merged
 * from every pack. The same lists remove recipes of any other type too (crafting, smelting, ...): those are taken out of
 * the server's recipe manager itself before it is synced, on every node. A recipe file of any type may also carry
 * {@code "golem:requires": [flags]} ({@link #flag}): it is removed the same way while one of them does not hold (a
 * mod's config branches, e.g. a recipe that exists only in an easy mode).
 *
 * <p>The mod states the row format once, as a {@link MapCodec} built from plain codecs (ids, tags, numbers); Golem
 * Core registers the type and its serializer on every node (the recipe interfaces changed twice between the
 * targets), never shows the rows in a recipe book, and keeps one snapshot of the rows per table, sorted by recipe id:
 * on the server from the current recipe set (rebuilt after every reload), on a client from Golem Core's own sync
 * packet, sent on join and after every reload (26.1 sends no custom recipe type to clients by itself).
 *
 * <p>Machines read {@link Table#entries()} and rebuild their own lookup indexes when {@link Table#changed} fires.
 */
public final class RecipeTables {

    /** One row: the recipe id and the decoded value. */
    public record Entry<T>(String id, T value) {}

    private static final Map<String, Table<?>> TABLES = new ConcurrentHashMap<>();
    private static final Map<String, DeferredRegister<RecipeType<?>>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, DeferredRegister<RecipeSerializer<?>>> SERIALIZERS = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;

    private RecipeTables() {}

    /**
     * Declares the table {@code <namespace>:<name>}: its recipe type and serializer are registered with the
     * namespace's other content. Call once, at mod construction.
     */
    public static <T> Table<T> table(String namespace, String name, Supplier<MapCodec<T>> codec) {
        Table<T> table = new Table<>(namespace, name, codec);
        if (TABLES.putIfAbsent(table.id(), table) != null) {
            throw new IllegalStateException("Recipe table " + table.id() + " declared twice");
        }
        return table;
    }

    /** Every declared table, sorted by id; for tools (the scripting adapter). */
    public static List<Table<?>> all() {
        List<Table<?>> all = new ArrayList<>(TABLES.values());
        all.sort(Comparator.comparing(Table::id));
        return all;
    }

    /** Golem Core's entrypoints call this on every side. */
    public static void init() {
        GolemNetwork.declarePlayToClient(SyncPayload.CODEC);
        Lifecycle.onServerStarted(started -> {
            server = started;
            for (Table<?> table : TABLES.values()) table.refresh(started);
        });
        Lifecycle.onServerStopping(stopping -> {
            server = null;
            filtered = null;
        });
        Lifecycle.onDatapackReloaded(RecipeTables::removeListedRecipes);
        DataGenerator.install();
        Lifecycle.onDatapackSync((syncing, players) -> {
            server = syncing;
            for (Table<?> table : TABLES.values()) {
                table.refresh(syncing);
                SyncPayload payload = table.payload(syncing.registryAccess());
                for (ServerPlayer player : players) GolemNetwork.sendToPlayer(player, SyncPayload.CODEC, payload);
            }
        });
    }

    /** Golem Core's client entrypoints call this. */
    public static void initClient() {
        GolemNetwork.registerPlayToClient(SyncPayload.CODEC, (payload, context) -> {
            Table<?> table = TABLES.get(payload.table());
            if (table != null && context.level() != null) table.receive(payload.rows(), context.level().registryAccess());
        });
    }

    /**
     * The recipe ids a client last received for {@code table} in Golem Core's sync packet (empty before the first, or
     * for an unknown table). In singleplayer {@link Table#entries()} reads the integrated server directly, so this is
     * what shows that the sync itself works; the harness query {@code recipe_table} reports it.
     */
    public static List<String> receivedIds(String table) {
        Table<?> found = TABLES.get(table);
        return found == null ? List.of() : found.received;
    }

    /** A machine's recipe table. */
    public static final class Table<T> {

        private final String namespace;
        private final String name;
        private final Supplier<MapCodec<T>> codec;
        private final RegistryObject<RecipeType<?>> type;
        private final RegistryObject<RecipeSerializer<?>> serializer;
        private volatile List<Entry<T>> entries = List.of();
        private volatile Object source;
        private volatile List<String> received = List.of();
        private volatile RecipeFields fields;

        /** Fires after the rows changed (server reload, or a client receiving them), on that side's thread. */
        public final Hook<Runnable> changed = Hook.notifying();

        private Table(String namespace, String name, Supplier<MapCodec<T>> codec) {
            this.namespace = namespace;
            this.name = name;
            this.codec = codec;
            this.type = TYPES.computeIfAbsent(namespace,
                            ns -> DeferredRegister.create(() -> Registries.RECIPE_TYPE, ns))
                    .register(name, () -> new RecipeType<TableRecipe<?>>() {
                        @Override
                        public String toString() {
                            return namespace + ":" + name;
                        }
                    });
            this.serializer = SERIALIZERS.computeIfAbsent(namespace,
                            ns -> DeferredRegister.create(() -> Registries.RECIPE_SERIALIZER, ns))
                    .register(name, () -> serializerFor(this));
        }

        /** {@code <namespace>:<name>}, the recipe JSON's {@code "type"}. */
        public String id() {
            return namespace + ":" + name;
        }

        public MapCodec<T> codec() {
            return codec.get();
        }

        /**
         * States the row's fields for scripts ({@link RecipeFields}); call once, at declaration. Returns this table.
         */
        public Table<T> fields(RecipeFields stated) {
            fields = java.util.Objects.requireNonNull(stated);
            return this;
        }

        /**
         * The row's fields: the stated ones, then one {@link RecipeFields.Kind#JSON} field for every other key of the
         * codec (so a subclass's extra keys, or a table that stated nothing, are still writable).
         */
        public RecipeFields fields() {
            RecipeFields result = fields == null ? RecipeFields.of() : fields;
            java.util.Iterator<String> keys = codec().keys(JsonOps.INSTANCE)
                    .map(key -> JsonOps.INSTANCE.getStringValue(key).result().orElse(null)).iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key != null && result.get(key) == null) {
                    result = result.add(RecipeFields.field(key, RecipeFields.Kind.JSON).optional());
                }
            }
            return result;
        }

        /** The current rows, sorted by recipe id: the server's after its last reload, or the last ones synced. */
        public List<Entry<T>> entries() {
            MinecraftServer current = server;
            if (current != null) refresh(current);
            return entries;
        }

        /** Replaces the rows, as a reload would; for tests and tools only. */
        public void replace(List<Entry<T>> rows) {
            List<Entry<T>> sorted = new ArrayList<>(rows);
            sorted.sort(Comparator.comparing(Entry::id));
            entries = List.copyOf(sorted);
            source = null;
            for (Runnable listener : changed) listener.run();
        }

        /** Encodes one value to JSON, as a datapack file would hold it (without "type"). */
        public JsonElement toJson(T value, HolderLookup.Provider registries) {
            return unwrap(codec().codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), value), id());
        }

        /** Decodes one value from JSON. */
        public T fromJson(JsonElement json, HolderLookup.Provider registries) {
            return unwrap(codec().codec().parse(RegistryOps.create(JsonOps.INSTANCE, registries), json), id());
        }

        private synchronized void refresh(MinecraftServer server) {
            RecipeManager manager = server.getRecipeManager();
            if (manager == source) return;
            List<Entry<T>> rows = new ArrayList<>();
            collect(manager, rows);
            java.util.Set<String> removed = removals(server);
            if (!removed.isEmpty()) rows.removeIf(row -> removed.contains(row.id()));
            rows.sort(Comparator.comparing(Entry::id));
            entries = List.copyOf(rows);
            source = manager;
            for (Runnable listener : changed) listener.run();
        }

        @SuppressWarnings("unchecked")
        private void collect(RecipeManager manager, List<Entry<T>> rows) {
            //? if >=26.1 {
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value() instanceof TableRecipe<?> recipe && recipe.table == this) {
                    rows.add(new Entry<>(Ids.keyId(holder.id()).toString(), (T) recipe.value));
                }
            }
            //?} elif >=1.20.5 {
            /*for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value() instanceof TableRecipe<?> recipe && recipe.table == this) {
                    rows.add(new Entry<>(holder.id().toString(), (T) recipe.value));
                }
            }
            *///?} else {
            /*for (Recipe<?> any : manager.getRecipes()) {
                if (any instanceof TableRecipe<?> recipe && recipe.table == this) {
                    rows.add(new Entry<>(recipe.getId().toString(), (T) recipe.value));
                }
            }
            *///?}
        }

        private SyncPayload payload(HolderLookup.Provider registries) {
            List<String> rows = new ArrayList<>();
            for (Entry<T> entry : entries) {
                rows.add(entry.id());
                rows.add(toJson(entry.value(), registries).toString());
            }
            return new SyncPayload(id(), rows);
        }

        private void receive(List<String> rows, HolderLookup.Provider registries) {
            List<String> ids = new ArrayList<>(rows.size() / 2);
            for (int i = 0; i + 1 < rows.size(); i += 2) ids.add(rows.get(i));
            received = List.copyOf(ids);
            List<Entry<T>> decoded = new ArrayList<>(rows.size() / 2);
            for (int i = 0; i + 1 < rows.size(); i += 2) {
                decoded.add(new Entry<>(rows.get(i), fromJson(JsonParser.parseString(rows.get(i + 1)), registries)));
            }
            replace(decoded);
        }

        RecipeSerializer<?> serializerType() {
            return serializer.get();
        }

        RecipeType<?> recipeType() {
            return type.get();
        }
    }

    /**
     * Every recipe id listed in any datapack's {@code data/<namespace>/recipe_removals/<path>.json}
     * ({@code {"recipes": ["hbm:chemical_plant/oxygen", ...]}}), merged: how a mod or datapack removes another mod's table
     * rows without depending on which pack wins a file path (Fabric 1.20.1 orders mod packs arbitrarily). A mod keeps
     * its list in its own namespace, so two mods' lists never replace each other.
     */
    private static java.util.Set<String> removals(MinecraftServer server) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (JsonElement file : dev.golem.core.data.DataFiles.read(REMOVALS, server.getResourceManager()).values()) {
            if (!file.isJsonObject() || !file.getAsJsonObject().has("recipes")) continue;
            for (JsonElement id : file.getAsJsonObject().getAsJsonArray("recipes")) ids.add(id.getAsString());
        }
        return ids;
    }

    /** The top-level recipe key naming the {@link #flag flags} a recipe of any type needs (see {@link #REQUIRES}). */
    public static final String REQUIRES = "golem:requires";

    private static final Map<String, java.util.function.BooleanSupplier> FLAGS = new ConcurrentHashMap<>();

    /**
     * Declares the recipe flag {@code name}: a recipe file of any type (crafting, smelting, a machine table, ...) that
     * lists {@code "golem:requires": ["name"]} (or {@code "!name"}) is in the game only while every literal holds. Every
     * loader's recipe parser ignores the unknown top-level key, so the file loads as usual; Golem Core then takes the
     * recipes whose literals fail out of the server's recipe manager after every load, as it does for {@link #REMOVALS}
     * lists. Values are read at each datapack load, so a config change applies at the next {@code /reload}. A literal
     * naming an undeclared flag fails (the recipe is left out, with a warning). Call at mod construction.
     */
    public static void flag(String name, java.util.function.BooleanSupplier value) {
        if (name.isEmpty() || name.startsWith("!")) throw new IllegalArgumentException("Bad recipe flag name " + name);
        if (FLAGS.putIfAbsent(name, value) != null) throw new IllegalStateException("Recipe flag " + name + " declared twice");
    }

    /** Whether every literal holds now ({@code name} or {@code !name}); an empty list always holds. */
    public static boolean holds(Iterable<String> literals, String where) {
        for (String literal : literals) {
            boolean negated = literal.startsWith("!");
            String name = negated ? literal.substring(1) : literal;
            java.util.function.BooleanSupplier value = FLAGS.get(name);
            if (value == null) {
                org.slf4j.LoggerFactory.getLogger("Golem Core").warn(
                        "Recipe {} requires the undeclared flag {}; leaving it out", where, literal);
                return false;
            }
            if (value.getAsBoolean() == negated) return false;
        }
        return true;
    }

    /** The ids of every recipe file whose {@link #REQUIRES} literals do not hold now. */
    private static java.util.Set<String> unmet(MinecraftServer server) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        var files = dev.golem.core.data.DataFiles.read(dev.golem.core.data.DataFiles.recipeFolder(), server.getResourceManager());
        for (Map.Entry<String, JsonElement> file : files.entrySet()) {
            if (!file.getValue().isJsonObject()) continue;
            JsonElement requires = file.getValue().getAsJsonObject().get(REQUIRES);
            if (requires == null) continue;
            List<String> literals = new ArrayList<>();
            if (requires.isJsonArray()) for (JsonElement literal : requires.getAsJsonArray()) literals.add(literal.getAsString());
            else literals.add(requires.getAsString());
            if (!holds(literals, file.getKey())) ids.add(file.getKey());
        }
        return ids;
    }

    private static volatile Object filtered;

    /**
     * Removes every recipe whose id is listed in a {@link #REMOVALS} file from the server's recipe manager, whatever its
     * type (crafting, smelting, another mod's machine table, ...): after the server's first load and after every
     * {@code /reload}, before the recipes are sent to any player, so clients, recipe books and recipe viewers never see
     * them. Each reload builds a new recipe manager; the one already handled is skipped.
     */
    private static synchronized void removeListedRecipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == filtered) return;
        filtered = manager;
        java.util.Set<String> removed = removals(server);
        int listed = removed.size();
        java.util.Set<String> unmet = unmet(server);
        removed.addAll(unmet);
        if (removed.isEmpty()) return;
        int dropped = 0;
        //? if >=26.1 {
        List<RecipeHolder<?>> kept = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (removed.contains(Ids.keyId(holder.id()).toString())) dropped++;
            else kept.add(holder);
        }
        if (dropped == 0) return;
        // 26.1 has no public setter (the client no longer holds recipes); the field is widened by Core's AT/AW. The
        // derived views (recipe book displays, stonecutter list, property sets, NeoForge priorities) are rebuilt as
        // the server's own load does.
        manager.recipes = net.minecraft.world.item.crafting.RecipeMap.create(kept);
        manager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
        //?} elif >=1.20.5 {
        /*List<RecipeHolder<?>> kept = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (removed.contains(holder.id().toString())) dropped++;
            else kept.add(holder);
        }
        if (dropped == 0) return;
        manager.replaceRecipes(kept);
        *///?} else {
        /*List<Recipe<?>> kept = new ArrayList<>();
        for (Recipe<?> recipe : manager.getRecipes()) {
            if (removed.contains(recipe.getId().toString())) dropped++;
            else kept.add(recipe);
        }
        if (dropped == 0) return;
        manager.replaceRecipes(kept);
        *///?}
        org.slf4j.LoggerFactory.getLogger("Golem Core").info(
                "Removed {} recipe(s) listed in {} files or with unmet {} ({} id(s) listed, {} unmet)", dropped, REMOVALS,
                REQUIRES, listed, unmet.size());
    }

    /** The datapack folder of recipe removal lists. */
    public static final String REMOVALS = "recipe_removals";

    private static <V> V unwrap(DataResult<V> result, String table) {
        return result.result().orElseThrow(() -> new IllegalArgumentException("Recipe table " + table + ": "
                + result.error().map(error -> error.message()).orElse("no value")));
    }

    /** The datapack carrier of one row: never matches, never in a recipe book. */
    public static final class TableRecipe<T> implements Recipe<
            //? if >=1.20.5 {
            RecipeInput
            //?} else {
            /*Container
            *///?}
            > {

        final Table<T> table;
        final T value;
        //? if <1.20.5
        //private final net.minecraft.resources.ResourceLocation id;

        TableRecipe(Table<T> table, T value
                //? if <1.20.5
                //, net.minecraft.resources.ResourceLocation id
        ) {
            this.table = table;
            this.value = value;
            //? if <1.20.5
            //this.id = id;
        }

        @Override
        public boolean isSpecial() {
            return true;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public RecipeSerializer getSerializer() {
            return table.serializerType();
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public RecipeType getType() {
            return table.recipeType();
        }

        //? if >=26.1 {
        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean showNotification() {
            return false;
        }

        @Override
        public String group() {
            return "";
        }

        @Override
        public net.minecraft.world.item.crafting.PlacementInfo placementInfo() {
            return net.minecraft.world.item.crafting.PlacementInfo.NOT_PLACEABLE;
        }

        @Override
        public net.minecraft.world.item.crafting.RecipeBookCategory recipeBookCategory() {
            return net.minecraft.world.item.crafting.RecipeBookCategories.CRAFTING_MISC;
        }
        //?} elif >=1.20.5 {
        /*@Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }
        *///?} else {
        /*@Override
        public boolean matches(Container input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(Container input, net.minecraft.core.RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public net.minecraft.resources.ResourceLocation getId() {
            return id;
        }
        *///?}
    }

    private static <T> RecipeSerializer<?> serializerFor(Table<T> table) {
        //? if >=1.20.5 {
        MapCodec<TableRecipe<T>> codec = table.codec().xmap(value -> new TableRecipe<>(table, value), recipe -> recipe.value);
        // The row travels as its JSON text: one wire format for every table, decoded with the same codec as the file.
        StreamCodec<RegistryFriendlyByteBuf, TableRecipe<T>> stream = StreamCodec.of(
                (buffer, recipe) -> buffer.writeUtf(table.toJson(recipe.value, buffer.registryAccess()).toString(), 262144),
                buffer -> new TableRecipe<>(table,
                        table.fromJson(JsonParser.parseString(buffer.readUtf(262144)), buffer.registryAccess())));
        //?}
        //? if >=26.1 {
        return new RecipeSerializer<>(codec, stream);
        //?} elif >=1.20.5 {
        /*MapCodec<TableRecipe<T>> mapCodec = codec;
        return new RecipeSerializer<TableRecipe<T>>() {
            @Override
            public MapCodec<TableRecipe<T>> codec() {
                return mapCodec;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, TableRecipe<T>> streamCodec() {
                return stream;
            }
        };
        *///?} else {
        /*return new RecipeSerializer<TableRecipe<T>>() {
            @Override
            public TableRecipe<T> fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
                com.google.gson.JsonObject value = json.deepCopy();
                value.remove("type");
                return new TableRecipe<>(table, unwrap(table.codec().codec().parse(JsonOps.INSTANCE, value), table.id()), id);
            }

            @Override
            public TableRecipe<T> fromNetwork(net.minecraft.resources.ResourceLocation id, FriendlyByteBuf buffer) {
                return new TableRecipe<>(table, unwrap(table.codec().codec()
                        .parse(JsonOps.INSTANCE, JsonParser.parseString(buffer.readUtf(262144))), table.id()), id);
            }

            @Override
            public void toNetwork(FriendlyByteBuf buffer, TableRecipe<T> recipe) {
                buffer.writeUtf(unwrap(table.codec().codec().encodeStart(JsonOps.INSTANCE, recipe.value), table.id())
                        .toString(), 262144);
            }
        };
        *///?}
    }

    /** One table's rows for a client: recipe id and row JSON, alternating. */
    record SyncPayload(String table, List<String> rows) implements GolemPayload {

        static final GolemCodec<SyncPayload> CODEC = GolemCodec.of(SyncPayload.class, Ids.of("golemcore", "recipe_table"),
                (payload, buffer) -> {
                    buffer.writeUtf(payload.table());
                    buffer.writeVarInt(payload.rows().size());
                    for (String row : payload.rows()) buffer.writeUtf(row, 262144);
                },
                buffer -> {
                    String table = buffer.readUtf();
                    int size = buffer.readVarInt();
                    List<String> rows = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) rows.add(buffer.readUtf(262144));
                    return new SyncPayload(table, rows);
                });

        @Override
        public
                //? if >=26.1 {
                net.minecraft.resources.Identifier
                //?} else {
                /*net.minecraft.resources.ResourceLocation
                *///?}
                id() {
            return CODEC.id();
        }
    }
}
