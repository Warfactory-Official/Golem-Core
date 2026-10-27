package dev.golem.core.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.golem.core.Ids;
import dev.golem.core.platform.ForgeEntrypoint;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.IModelBuilder;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

final class UnbakedModelBindings {
    private UnbakedModelBindings() {}

    static void initialize() {
        ForgeEntrypoint.modBus().addListener((ModelEvent.RegisterGeometryLoaders event) -> event.register("groups", new Loader()));
        ForgeEntrypoint.modBus().addListener((ModelEvent.RegisterAdditional event) -> UnbakedModels.beginReload());
    }

    private static final class Loader implements IGeometryLoader<GeometryModel> {
        @Override
        public GeometryModel read(JsonObject json, JsonDeserializationContext context) {
            var id = Ids.parse(json.get("golem:loader").getAsString());
            var loader = UnbakedModels.loaders().get(id);
            if (loader == null) throw new IllegalArgumentException("Unregistered model loader: " + id);
            return new GeometryModel(loader.apply(json, context));
        }
    }

    private record GeometryModel(UnbakedModel model) implements IUnbakedGeometry<GeometryModel> {
        @Override
        public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver, IGeometryBakingContext context) {
            model.resolveParents(resolver);
        }

        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
                               Function<Material, TextureAtlasSprite> sprites, ModelState state, ItemOverrides overrides,
                               ResourceLocation location) {
            if (model instanceof GroupedModel groups) {
                var hint = context.getRenderTypeHint();
                var renderTypes = hint == null ? RenderTypeGroup.EMPTY : context.getRenderType(hint);
                var builder = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(), context.isGui3d(),
                        context.getTransforms(), overrides, sprites.apply(context.getMaterial("particle")), renderTypes);
                for (var quad : GroupedBaker.bake(groups, state, slot -> sprites.apply(context.getMaterial(slot)))) {
                    builder.addUnculledFace(quad);
                }
                return builder.build();
            }
            return model.bake(baker, sprites, state, location);
        }
    }
}
