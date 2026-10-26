package dev.golem.core.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.golem.core.Ids;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

final class UnbakedModelBindings {
    private UnbakedModelBindings() {}

    static void initialize() {
        PreparableModelLoadingPlugin.register((resources, executor) -> CompletableFuture.supplyAsync(() -> {
            UnbakedModels.beginReload();
            return new Reload(resources);
        }, executor), (reload, context) -> {
            context.modifyModelOnLoad().register((model, loaded) -> {
                //? if >=1.21.1 {
                var id = loaded.resourceId();
                //?} else {
                /*var id = loaded.id();
                *///?}
                if (id != null && model instanceof BlockModel) reload.remember(model, id);
                return model;
            });
            context.modifyModelBeforeBake().register((model, baking) -> {
                if (!(model instanceof BlockModel owner)) return model;
                var custom = reload.custom(model);
                if (custom == null) return model;
                if (custom instanceof GroupedModel groups) return groups.withBase(owner);
                custom.resolveParents(baking.baker()::getModel);
                return custom;
            });
        });
    }

    private record Definition(@Nullable JsonObject json, @Nullable ResourceLocation parent) {}

    private static final class Reload {
        private static final Definition ABSENT = new Definition(null, null);
        private final ResourceManager resources;
        private final Map<ResourceLocation, Definition> definitions = new HashMap<>();
        private final Map<ResourceLocation, UnbakedModel> models = new HashMap<>();
        private final Map<UnbakedModel, ResourceLocation> identities = new IdentityHashMap<>();

        Reload(ResourceManager resources) {
            this.resources = resources;
        }

        synchronized void remember(UnbakedModel model, ResourceLocation id) {
            identities.putIfAbsent(model, id);
        }

        synchronized @Nullable UnbakedModel custom(UnbakedModel model) {
            var id = identities.get(model);
            var visited = new HashSet<ResourceLocation>();
            while (id != null) {
                if (!visited.add(id)) throw new IllegalArgumentException("Grouped model parent cycle: " + id);
                var definition = definitions.computeIfAbsent(id, this::read);
                if (definition.json() != null) {
                    return models.computeIfAbsent(id, ignored -> {
                        var loader = Ids.parse(definition.json().get("golem:loader").getAsString());
                        var deserializer = UnbakedModels.loaders().get(loader);
                        if (deserializer == null) throw new IllegalArgumentException("Unregistered model loader: " + loader);
                        return deserializer.apply(definition.json(), UnbakedModels.CONTEXT);
                    });
                }
                id = definition.parent();
            }
            return null;
        }

        private Definition read(ResourceLocation id) {
            var path = Ids.of(id.getNamespace(), "models/" + id.getPath() + ".json");
            var resource = resources.getResource(path);
            if (resource.isEmpty()) return ABSENT;
            try (var reader = resource.get().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                var parent = json.has("parent") ? Ids.parse(json.get("parent").getAsString()) : null;
                return new Definition(json.has("golem:loader") ? json : null, parent);
            } catch (IOException failure) {
                throw new UncheckedIOException("Reading model descriptor " + path, failure);
            }
        }
    }
}
