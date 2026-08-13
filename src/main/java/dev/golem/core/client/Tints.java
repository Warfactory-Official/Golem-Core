package dev.golem.core.client;

//? if <26.1 {
/*import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.core.GolemCore;
import dev.golem.core.data.ModelData;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
*///?}

/**
 * Item tint colours, as 26.1 does them, on versions that have no way to express them.
 *
 * <p>From 1.21.4 an item definition declares its own tints, one entry per tint index:
 * {@code "tints": [{"type":"minecraft:constant","value":-1}, {"type":"minecraft:custom_model_data","index":0,"default":16777215}]}
 * — here layer 0 is left alone and layer 1 takes the colour {@code ModelData.setColor} stored. Below
 * that there is no item-definition layer and no tint-source concept, only the old per-item
 * {@code ItemColor} registry, reached differently on each loader. All of it lives here, so a mod calls
 * {@link #install(String)} once from shared code and never learns which era or loader it is on.
 *
 * <p>The tint <em>array is read from the same item definitions 26.1 reads</em> rather than assumed.
 * Assuming index 0 is tinted inverts every two-layer item — the container gets the fluid's colour and
 * the fluid stays white — which is exactly what a container/overlay pair declares the other way round.
 * Vanilla never opens these files below 1.21.4, so parsing them here supplies the missing step rather
 * than duplicating vanilla's work, and keeps the framing data resource-pack-overridable.
 *
 * <p>On 26.1 everything here is a no-op: the engine already does all of it.
 */
public final class Tints {

    private Tints() {}

    /** 26.1's declared tint default — plain white RGB, i.e. "leave the texture alone". */
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
     * both eras, so full alpha is added. {@code colorIndex} is kept for fidelity even though this era
     * can only store one colour per stack — every tint in practice reads index 0.
     ^/
    private record Tint(boolean constant, int value, int colorIndex, int defaultColor) {

        int resolve(ItemStack stack) {
            return constant ? value : OPAQUE | (ModelData.color(stack, defaultColor) & 0xFFFFFF);
        }
    }

    /^* Item id -> its tints, by tint index. Rebuilt on every resource reload. ^/
    private static Map<ResourceLocation, List<Tint>> tints = Map.of();
    *///?}

    /**
     * Applies 26.1 tint behaviour to every item in {@code namespace}. Call from client init, on every
     * version and both loaders; below 26.1 it wires up the loader's colour registry and a reload
     * listener for the tint declarations, and on 26.1 it returns immediately.
     */
    public static void install(String namespace) {
        //? if <26.1 {
        /*ItemColor color = (stack, tintIndex) -> {
            List<Tint> declared = tints.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            // Past the end of the declared array — or on an item that declares none — vanilla's own
            // answer, which is bit-identical to having registered no handler at all.
            if (declared == null || tintIndex < 0 || tintIndex >= declared.size()) return NO_TINT;
            return declared.get(tintIndex).resolve(stack);
        };

        //? if fabric {
        // Fabric's registry takes items now. The client initializer runs after the main one, so the
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
                        return ResourceLocation.fromNamespaceAndPath(GolemCore.MOD_ID, "item_tints");
                    }

                    @Override
                    public void onResourceManagerReload(ResourceManager resources) {
                        // Qualified: the listener interface inherits its own reload(...) overload.
                        Tints.reload(resources);
                    }
                });
        //?} elif neoforge {
        // NeoForge takes item colours through a mod-bus event, and this runs during mod construction —
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
        //?}
        *///?}
    }

    /**
     * Registers a {@link BlockTint} for the given blocks, on whichever colour API this node has.
     *
     * <p>26.1 registers a list of {@code BlockTintSource}s per block; older versions register a single
     * {@code BlockColor} callback, which this adapts to by dropping the level, position and tint index
     * that a state-keyed tint never used. Both go on the client's own {@code BlockColors}, so the only
     * loader-specific part is <em>when</em> that is safe to touch — see {@link #whenColorsRegisterable}.
     *
     * <p>Blocks are taken as suppliers and resolved inside that deferred action, not at the call site.
     * NeoForge runs client init during mod construction, before deferred registration has created
     * anything, so resolving eagerly throws {@code Missing key in ResourceKey[...]} on the first block.
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
                public java.util.Set<net.minecraft.world.level.block.state.properties.Property<?>> relevantProperties() {
                    return tint.relevantProperties();
                }
            }), resolved);
            //?} else {
            /*colors.register((state, level, pos, tintIndex) -> tint.color(state), resolved);
            *///?}
        });
    }

    /**
     * Runs {@code action} at the first moment this loader allows colour handlers to be registered.
     *
     * <p>Fabric's client initializer runs before the {@code Minecraft} instance exists, and NeoForge's
     * mod construction runs earlier still, so neither can touch {@code BlockColors} directly. Both offer
     * a hook that fires once the client is built; these two are the ones that exist unchanged across
     * every version in the matrix, which is what keeps this free of era guards.
     */
    private static void whenColorsRegisterable(Runnable action) {
        //? if fabric {
        /*net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> action.run());
        *///?} elif neoforge {
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(action));
        //?}
    }

    //? if <26.1 {
    /*/^* Reparses every item definition's tint declarations. ^/
    private static void reload(ResourceManager resources) {
        Map<ResourceLocation, List<Tint>> built = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry :
                resources.listResources("items", path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                List<Tint> parsed = tintsOf(JsonParser.parseReader(reader));
                if (parsed.isEmpty()) continue;
                String path = file.getPath();
                String name = path.substring("items/".length(), path.length() - ".json".length());
                built.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name), parsed);
            } catch (Exception e) {
                // A malformed definition costs that item its tint, not the whole reload.
                GolemCore.LOG.warn("Skipping tints for {}: {}", file, e.toString());
            }
        }
        tints = Map.copyOf(built);
        GolemCore.LOG.info("Tint emulation loaded declarations for {} item(s)", tints.size());
    }

    /^*
     * The tints an item definition declares, in tint-index order; empty if it declares none.
     *
     * <p>A `minecraft:condition` selects between two models that each carry their own tints. This era
     * cannot switch tint arrays per condition, so the false branch wins — in practice both branches of
     * such a pair declare the same tints, and they differ only in which texture they draw.
     ^/
    private static List<Tint> tintsOf(JsonElement root) {
        if (!root.isJsonObject()) return List.of();
        JsonElement model = root.getAsJsonObject().get("model");
        if (model == null || !model.isJsonObject()) return List.of();
        JsonObject object = model.getAsJsonObject();

        JsonElement onFalse = object.get("on_false");
        if (onFalse != null && onFalse.isJsonObject()) {
            List<Tint> branch = tintsIn(onFalse.getAsJsonObject());
            if (!branch.isEmpty()) return branch;
            JsonElement onTrue = object.get("on_true");
            if (onTrue != null && onTrue.isJsonObject()) return tintsIn(onTrue.getAsJsonObject());
        }
        return tintsIn(object);
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
                parsed.add(new Tint(true, tint.get("value").getAsInt(), 0, 0));
            } else if ("minecraft:custom_model_data".equals(type)) {
                int index = tint.has("index") ? tint.get("index").getAsInt() : 0;
                int fallback = tint.has("default") ? tint.get("default").getAsInt() : UNTINTED;
                parsed.add(new Tint(false, 0, index, fallback));
            } else {
                // An unknown source leaves that index untinted rather than guessing at its meaning.
                parsed.add(new Tint(true, NO_TINT, 0, 0));
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
