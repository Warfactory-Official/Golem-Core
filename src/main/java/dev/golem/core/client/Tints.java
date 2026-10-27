package dev.golem.core.client;

//? if neoforge {
import dev.golem.core.platform.NeoForgeEntrypoint;
//?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
//?}

import dev.golem.core.GolemCore;
import dev.golem.core.Ids;
import java.util.function.ToIntFunction;
import java.util.function.Function;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonParseException;
import java.io.IOException;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
//?}

//? if <26.1 {
/*import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.core.data.ModelData;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
*///?}

/**
 * Item and block tints through native declarations and legacy color callbacks.
 * Legacy callbacks read selected leaves from the same item definitions and keep component colors opaque.
 */
public final class Tints {

    private Tints() {}
    private static final Map<String, ToIntFunction<ItemStack>> SOURCES = new ConcurrentHashMap<>();

    /** 26.1's declared tint default: plain white RGB, which leaves the texture alone. */
    public static final int UNTINTED = 0xFFFFFF;

    //? if <26.1 {
    /*/^* Full alpha. Pre-26.1 item tints are ARGB and a zero alpha erases the item entirely. ^/
    private static final int OPAQUE = 0xFF000000;

    /^* What vanilla's own ItemColors returns for an untinted quad: opaque white. ^/
    private static final int NO_TINT = -1;

    /^*
     * One entry of an item definition's `tints` array.
     *
     * <p>A constant carries a finished ARGB value and is returned verbatim. A custom-model-data tint
     * reads a colour the stack carries, falling back to the declared default; that colour is RGB in
     * both eras, so full alpha is added. {@code colorIndex} picks the colour: index 0 is the one
     * {@code ModelData.setColor} stores, higher indices come from {@code ModelData.setColors}.
     ^/
    private record Tint(boolean constant, int value, int colorIndex, int defaultColor, ToIntFunction<ItemStack> source) {

        int resolve(ItemStack stack) {
            if (source != null) return source.applyAsInt(stack);
            return constant ? value : OPAQUE | (ModelData.color(stack, colorIndex, defaultColor) & 0xFFFFFF);
        }
    }

    /^* Item id -> its tints, by tint index. Rebuilt on every resource reload. ^/
    private static Map<ResourceLocation, Function<ItemStack, List<Tint>>> tints = Map.of();
    *///?}

    /**
     * Applies 26.1 tint behaviour to every item in {@code namespace}. Call from client init, on every
     * version and both loaders; below 26.1 it wires up the loader's colour registry and a reload
     * listener for the tint declarations, and on 26.1 it returns immediately.
     */
    public static void install(String namespace) {
        //? if <26.1 {
        /*ItemColor color = (stack, tintIndex) -> {
            var selector = tints.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            var declared = selector == null ? List.<Tint>of() : selector.apply(stack);
            // Past the end of the declared array, or on an item that declares none, vanilla's own
            // answer, which is bit-identical to having registered no handler at all.
            if (declared == null || tintIndex < 0 || tintIndex >= declared.size()) return NO_TINT;
            return declared.get(tintIndex).resolve(stack);
        };

        //? if fabric {
        /^// Fabric's registry takes items now. The client initializer runs after the main one, so the
        // mod's items are already in BuiltInRegistries by this point.
        List<Item> items = itemsIn(namespace);
        for (Item item : items) {
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(color, item);
        }
        report(namespace, items.size());

        net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return Ids.of(GolemCore.MOD_ID, "item_tints");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resources) {
                        // Qualified: the listener interface inherits its own reload(...) overload.
                        Tints.reload(resources);
                    }
                });
        ^///?} elif neoforge {
        // NeoForge takes item colours through a mod-bus event, and this runs during mod construction:
        // long before deferred registration has put anything in BuiltInRegistries. Collecting the items
        // here would silently find none, so the lookup happens inside the listener, by which time the
        // registries are populated and frozen.
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) -> {
                    List<Item> items = itemsIn(namespace);
                    if (!items.isEmpty()) event.register(color, items.toArray(new Item[0]));
                    report(namespace, items.size());
                });

        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) ->
                        event.registerReloadListener(
                                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) Tints::reload));
        //?} elif forge {
        /^dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) -> {
                    List<Item> items = itemsIn(namespace);
                    if (!items.isEmpty()) event.register(color, items.toArray(new Item[0]));
                    report(namespace, items.size());
                });

        dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) ->
                        event.registerReloadListener(
                                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) Tints::reload));
        ^///?}
        *///?}
    }

    /** The same item tint source resolves native declarations and selected legacy model leaves. */
    public static void registerItemSource(String namespace, String name, ToIntFunction<ItemStack> color) {
        if (SOURCES.putIfAbsent(namespace + ":" + name, color) != null) {
            throw new IllegalArgumentException("Item tint source already registered: " + namespace + ":" + name);
        }
        //? if >=26.1 {
        var source = new CallbackTint(color);
        //? if fabric {
        /*ItemTintSources.ID_MAPPER.put(Ids.of(namespace, name), source.type());
        *///?} elif neoforge {
        NeoForgeEntrypoint.modBus().addListener(
                (RegisterColorHandlersEvent.ItemTintSources event) ->
                        event.register(Ids.of(namespace, name), source.type()));
        //?}
        //?}
    }

    //? if >=26.1 {
    private static final class CallbackTint implements ItemTintSource {
        private final ToIntFunction<ItemStack> color;
        private final MapCodec<CallbackTint> codec;

        private CallbackTint(ToIntFunction<ItemStack> color) {
            this.color = color;
            codec = MapCodec.unit(this);
        }

        @Override public int calculate(ItemStack stack, ClientLevel level, LivingEntity owner) {
            return color.applyAsInt(stack);
        }

        @Override public MapCodec<CallbackTint> type() { return codec; }
    }
    //?}

    /**
     * Registers a {@link BlockTint} for the given blocks, on whichever colour API this node has.
     *
     * <p>26.1 registers a list of {@code BlockTintSource}s per block; older versions register a single
     * {@code BlockColor} callback, which passes the level and position on to {@link BlockTint#colorInWorld}.
     * See {@link #whenColorsRegisterable} for when this is safe.
     *
     * <p>Blocks are suppliers resolved inside the deferred action: NeoForge runs client init during mod
     * construction, before deferred registration has created anything, so resolving eagerly throws
     * {@code Missing key in ResourceKey[...]}.
     */
    @SafeVarargs
    public static void registerBlockTint(BlockTint tint,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
        whenColorsRegisterable(() -> {
            net.minecraft.client.color.block.BlockColors colors =
                    net.minecraft.client.Minecraft.getInstance().getBlockColors();
            net.minecraft.world.level.block.Block[] resolved =
                    new net.minecraft.world.level.block.Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) resolved[i] = blocks[i].get();
            //? if >=26.1 {
            colors.register(java.util.List.of(new net.minecraft.client.color.block.BlockTintSource() {
                @Override
                public int color(net.minecraft.world.level.block.state.BlockState state) {
                    return tint.color(state);
                }

                @Override
                public int colorInWorld(net.minecraft.world.level.block.state.BlockState state,
                                        net.minecraft.client.renderer.block.BlockAndTintGetter level,
                                        net.minecraft.core.BlockPos pos) {
                    return tint.colorInWorld(state, level, pos);
                }

                @Override
                public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
                    return tint.relevantProperties();
                }
            }), resolved);
            //?} else {
            /*colors.register((state, level, pos, tintIndex) -> level == null || pos == null ? tint.color(state)
                    : tint.colorInWorld(state, level, pos), resolved);
            *///?}
            GolemCore.LOG.info("Block tint installed for {} block(s)", resolved.length);
        });
    }

    /**
     * Registers one state-keyed tint per tint index: {@code byIndex.get(n)} colours every quad whose model
     * declared {@code tintindex n}. A quad whose index is outside the list keeps white, as in vanilla.
     */
    @SafeVarargs
    public static void registerBlockTints(java.util.List<BlockTint> byIndex,
            java.util.function.Supplier<? extends net.minecraft.world.level.block.Block>... blocks) {
        java.util.List<BlockTint> tints = java.util.List.copyOf(byIndex);
        whenColorsRegisterable(() -> {
            net.minecraft.client.color.block.BlockColors colors =
                    net.minecraft.client.Minecraft.getInstance().getBlockColors();
            net.minecraft.world.level.block.Block[] resolved =
                    new net.minecraft.world.level.block.Block[blocks.length];
            for (int i = 0; i < blocks.length; i++) resolved[i] = blocks[i].get();
            //? if >=26.1 {
            java.util.List<net.minecraft.client.color.block.BlockTintSource> sources = new java.util.ArrayList<>();
            for (BlockTint tint : tints) {
                sources.add(new net.minecraft.client.color.block.BlockTintSource() {
                    @Override
                    public int color(net.minecraft.world.level.block.state.BlockState state) {
                        return tint.color(state);
                    }

                    @Override
                    public int colorInWorld(net.minecraft.world.level.block.state.BlockState state,
                                            net.minecraft.client.renderer.block.BlockAndTintGetter level,
                                            net.minecraft.core.BlockPos pos) {
                        return tint.colorInWorld(state, level, pos);
                    }

                    @Override
                    public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
                        return tint.relevantProperties();
                    }
                });
            }
            colors.register(sources, resolved);
            //?} else {
            /*colors.register((state, level, pos, tintIndex) -> tintIndex < 0 || tintIndex >= tints.size() ? -1
                    : level == null || pos == null ? tints.get(tintIndex).color(state)
                    : tints.get(tintIndex).colorInWorld(state, level, pos), resolved);
            *///?}
            GolemCore.LOG.info("Indexed block tints ({}) installed for {} block(s)", tints.size(), resolved.length);
        });
    }

    /**
     * Runs {@code action} at the first moment this loader allows colour handlers to be registered.
     *
     * <p>Fabric's client initializer and NeoForge's mod construction both run before the {@code Minecraft}
     * instance exists, so neither can touch {@code BlockColors} directly. Each offers a hook that fires once the
     * client is built.
     */
    private static void whenColorsRegisterable(Runnable action) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> action.run());
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(action));
        //?} elif forge {
        /*dev.golem.core.platform.ForgeEntrypoint.modBus().addListener(
                (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(action));
        *///?}
    }

    //? if <26.1 {
    /*/^* Reparses every item definition's tint declarations. ^/
    private static void reload(ResourceManager resources) {
        var built = new HashMap<ResourceLocation, Function<ItemStack, List<Tint>>>();
        var definitions = new HashMap<ResourceLocation, JsonObject>();
        for (var entry : resources.listResources("items", path -> path.getPath().endsWith(".json")).entrySet()) {
            var file = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                var definition = JsonParser.parseReader(reader).getAsJsonObject();
                var path = file.getPath();
                var name = path.substring("items/".length(), path.length() - ".json".length());
                var id = Ids.of(file.getNamespace(), name);
                definitions.put(id, definition);
                var parsed = tintsOf(definition);
                if (parsed != null) built.put(id, parsed);
            } catch (IOException | JsonParseException malformed) {
                // Unreadable or malformed item definitions retain vanilla's untinted result.
                GolemCore.LOG.warn("Skipping tints for {}: {}", file, malformed.toString());
            }
        }
        ItemModelProperties.definitions(definitions);
        tints = Map.copyOf(built);
        GolemCore.LOG.info("Tint emulation loaded declarations for {} item(s)", tints.size());
    }

    private static Function<ItemStack, List<Tint>> tintsOf(JsonObject root) {
        var model = root.getAsJsonObject("model");
        if (model == null) return null;
        if (model.has("type") && model.get("type").getAsString().equals("minecraft:select")) {
            var property = model.get("property").getAsString();
            if (!ItemModelProperties.registeredSelect(property)) return null;
            var fallback = tintsIn(model.getAsJsonObject("fallback"));
            var cases = new HashMap<String, List<Tint>>();
            var hasTint = !fallback.isEmpty();
            for (var entry : model.getAsJsonArray("cases")) {
                var branch = entry.getAsJsonObject();
                var selected = tintsIn(branch.getAsJsonObject("model"));
                cases.put(branch.get("when").getAsString(), selected);
                hasTint |= !selected.isEmpty();
            }
            if (!hasTint) return null;
            var branches = Map.copyOf(cases);
            return stack -> {
                var value = ItemModelProperties.selected(property, stack);
                return value == null ? fallback : branches.getOrDefault(value, fallback);
            };
        }
        var selected = tintsIn(model);
        if (model.has("on_false")) {
            selected = tintsIn(model.getAsJsonObject("on_false"));
            if (selected.isEmpty() && model.has("on_true")) selected = tintsIn(model.getAsJsonObject("on_true"));
        }
        var result = selected;
        return result.isEmpty() ? null : stack -> result;
    }

    private static List<Tint> tintsIn(JsonObject model) {
        JsonElement declared = model.get("tints");
        if (declared == null || !declared.isJsonArray()) return List.of();
        JsonArray array = declared.getAsJsonArray();
        List<Tint> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            JsonObject tint = element.getAsJsonObject();
            String type = tint.get("type").getAsString();
            if ("minecraft:constant".equals(type)) {
                parsed.add(new Tint(true, tint.get("value").getAsInt(), 0, 0, null));
            } else if ("minecraft:custom_model_data".equals(type)) {
                int index = tint.has("index") ? tint.get("index").getAsInt() : 0;
                int fallback = tint.has("default") ? tint.get("default").getAsInt() : UNTINTED;
                parsed.add(new Tint(false, 0, index, fallback, null));
            } else if (SOURCES.containsKey(type)) {
                parsed.add(new Tint(false, 0, 0, 0, SOURCES.get(type)));
            } else {
                // An unknown source leaves that index untinted rather than guessing at its meaning.
                parsed.add(new Tint(true, NO_TINT, 0, 0, null));
            }
        }
        return List.copyOf(parsed);
    }

    private static List<Item> itemsIn(String namespace) {
        List<Item> items = new ArrayList<>();
        BuiltInRegistries.ITEM.entrySet().stream()
                .filter(e -> e.getKey().location().getNamespace().equals(namespace))
                .forEach(e -> items.add(e.getValue()));
        return items;
    }

    /^* A tint that never got installed is indistinguishable from a white texture, so say either way. ^/
    private static void report(String namespace, int count) {
        if (count == 0) {
            GolemCore.LOG.warn("Tint emulation found no {} items to cover", namespace);
        } else {
            GolemCore.LOG.info("Tint emulation installed for {} {} item(s)", count, namespace);
        }
    }
    *///?}
}
