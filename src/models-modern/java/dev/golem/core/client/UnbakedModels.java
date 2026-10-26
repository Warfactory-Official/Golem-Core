package dev.golem.core.client;

//? if neoforge {
import dev.golem.core.platform.NeoForgeEntrypoint;
//?}
//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
*///?}
//? if fabric && >=26.1 {
/*import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
*///?}
//? if neoforge && >=26.1 {
import net.neoforged.neoforge.client.event.ModelEvent;
//?}

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import dev.golem.core.render.gem.Geometry;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;

/** Shared unbaked model registration for world block states and item models. */
public final class UnbakedModels {
    private UnbakedModels() {}
    private static final Map<Identifier, BiFunction<JsonObject, JsonDeserializationContext, UnbakedModel>> LOADERS =
            new LinkedHashMap<>();
    private static boolean initialized;
    private static boolean sealed;

    /** Called by Core's client entrypoint before dependent mods register their model loaders. */
    public static synchronized void initialize() {
        if (initialized) throw new IllegalStateException("Unbaked model registration was initialized twice");
        initialized = true;
        //? if fabric {
        /*ModelLoadingPlugin.register(context -> seal());
        *///?} elif neoforge {
        NeoForgeEntrypoint.modBus().addListener(
                (ModelEvent.RegisterLoaders event) -> {
                    synchronized (UnbakedModels.class) {
                        seal();
                        LOADERS.forEach((id, loader) -> event.register(id, loader::apply));
                    }
                });
        //?}
    }

    private static synchronized void seal() {
        sealed = true;
    }

    public static void registerGroups(Identifier id, Function<Identifier, Geometry.Groups> loader) {
        register(id, (json, context) -> GroupedModels.read(id, loader, json, context));
    }

    public static synchronized void register(Identifier id,
            BiFunction<JsonObject, JsonDeserializationContext, UnbakedModel> deserializer) {
        if (!initialized || sealed) throw new IllegalStateException("Register unbaked model loaders during client initialization: " + id);
        if (LOADERS.putIfAbsent(id, deserializer) != null) {
            throw new IllegalArgumentException("Unbaked model loader is already registered: " + id);
        }
        //? if fabric {
        /*UnbakedModelDeserializer.register(id, deserializer::apply);
        *///?}
    }
}
