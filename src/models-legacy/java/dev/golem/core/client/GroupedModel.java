package dev.golem.core.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.golem.core.Ids;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

final class GroupedModel implements UnbakedModel {
    private final BlockModel base;
    private final ResourceLocation loaderId;
    private final ResourceLocation source;
    private final Map<String, List<String>> skins;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;

    GroupedModel(ResourceLocation loaderId, JsonObject json, JsonDeserializationContext context) {
        if (json.has("groups") || json.has("hidden") || json.has("transform") || json.has("visibility")) {
            throw new IllegalArgumentException("Group poses, visibility and root transforms require an authored baking policy");
        }
        var inherited = json.deepCopy();
        inherited.remove("fabric:type");
        inherited.remove("loader");
        inherited.remove("golem:loader");
        base = context.deserialize(inherited, BlockModel.class);
        this.loaderId = loaderId;
        source = Ids.parse(json.get("model").getAsString());
        skins = new LinkedHashMap<>();
        for (var entry : json.getAsJsonObject("skins").entrySet()) {
            skins.put(entry.getKey(), entry.getValue().getAsJsonArray().asList().stream()
                    .map(value -> value.getAsString()).toList());
        }
        offsetX = 0.5F + number(json, "offset_x");
        offsetY = number(json, "offset_y");
        offsetZ = 0.5F + number(json, "offset_z");
    }

    private static float number(JsonObject json, String name) {
        return json.has(name) ? json.get(name).getAsFloat() : 0F;
    }

    private GroupedModel(GroupedModel model, BlockModel owner) {
        base = owner;
        loaderId = model.loaderId;
        source = model.source;
        skins = model.skins;
        offsetX = model.offsetX;
        offsetY = model.offsetY;
        offsetZ = model.offsetZ;
    }

    GroupedModel withBase(BlockModel owner) {
        return new GroupedModel(this, owner);
    }

    ResourceLocation source() { return source; }
    Map<String, List<String>> skins() { return skins; }
    float offsetX() { return offsetX; }
    float offsetY() { return offsetY; }
    float offsetZ() { return offsetZ; }

    float[] vertices(List<String> parts) {
        return UnbakedModels.vertices(loaderId, source, parts);
    }

    BakedModel bakeWith(BlockModel owner, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                        ModelState state) {
        var overrides = owner.getOverrides().isEmpty() ? ItemOverrides.EMPTY
                : new ItemOverrides(baker, owner, owner.getOverrides());
        var builder = new SimpleBakedModel.Builder(owner, overrides, true)
                .particle(sprites.apply(owner.getMaterial("particle")));
        for (var quad : GroupedBaker.bake(this, state, slot -> sprites.apply(owner.getMaterial(slot)))) {
            builder.addUnculledFace(quad);
        }
        return builder.build();
    }

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return base.getDependencies();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
        base.resolveParents(resolver);
    }

    //? if >=1.21.1 {
    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        return bakeWith(base, baker, sprites, state);
    }
    //?} else {
    /*@Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> sprites, ModelState state,
                           ResourceLocation location) {
        return bakeWith(base, baker, sprites, state);
    }
    *///?}
}
