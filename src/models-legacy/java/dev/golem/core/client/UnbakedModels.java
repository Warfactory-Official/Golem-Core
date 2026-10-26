package dev.golem.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.golem.core.render.gem.Geometry;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

/** Resource-backed model registrations shared by the legacy loader bindings. */
public final class UnbakedModels {
    private static final Map<ResourceLocation, BiFunction<JsonObject, JsonDeserializationContext, UnbakedModel>> LOADERS =
            new LinkedHashMap<>();
    private static final Map<ResourceLocation, Function<ResourceLocation, Geometry.Groups>> GROUPS = new LinkedHashMap<>();
    private static final Map<MeshKey, Geometry.Groups> MESHES = new LinkedHashMap<>();
    private static final Map<PartKey, float[]> PARTS = new LinkedHashMap<>();
    private static final Gson JSON = new Gson();
    private static boolean initialized;
    private static boolean sealed;

    private UnbakedModels() {}

    public static synchronized void initialize() {
        if (initialized) throw new IllegalStateException("Unbaked model registration was initialized twice");
        initialized = true;
        UnbakedModelBindings.initialize();
    }

    public static synchronized void register(ResourceLocation id,
            BiFunction<JsonObject, JsonDeserializationContext, UnbakedModel> deserializer) {
        if (!initialized || sealed) throw new IllegalStateException("Register model loaders during client initialization: " + id);
        if (LOADERS.putIfAbsent(id, deserializer) != null) {
            throw new IllegalArgumentException("Unbaked model loader is already registered: " + id);
        }
    }

    public static synchronized void registerGroups(ResourceLocation id, Function<ResourceLocation, Geometry.Groups> loader) {
        register(id, (json, context) -> new GroupedModel(id, json, context));
        GROUPS.put(id, loader);
    }

    static synchronized Map<ResourceLocation, BiFunction<JsonObject, JsonDeserializationContext, UnbakedModel>> loaders() {
        sealed = true;
        return Map.copyOf(LOADERS);
    }

    static synchronized void beginReload() {
        sealed = true;
        MESHES.clear();
        PARTS.clear();
    }

    static synchronized float[] vertices(ResourceLocation loader, ResourceLocation source, List<String> names) {
        var meshKey = new MeshKey(loader, source);
        var key = new PartKey(meshKey, List.copyOf(names));
        return PARTS.computeIfAbsent(key, ignored -> {
            var mesh = MESHES.computeIfAbsent(meshKey, input -> GROUPS.get(input.loader()).apply(input.source()));
            return mesh.parts(names.toArray(String[]::new)).vertices();
        });
    }

    static final JsonDeserializationContext CONTEXT = new JsonDeserializationContext() {
        @Override
        @SuppressWarnings("unchecked")
        public <T> T deserialize(JsonElement json, Type type) {
            if (type == BlockModel.class || type == UnbakedModel.class) {
                return (T) BlockModel.fromString(json.toString());
            }
            return JSON.fromJson(json, type);
        }
    };

    private record MeshKey(ResourceLocation loader, ResourceLocation source) {}
    private record PartKey(MeshKey mesh, List<String> names) {}
}
