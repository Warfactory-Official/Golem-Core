package dev.golem.core.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.golem.core.Ids;
import dev.golem.core.render.gem.Geometry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

final class GroupedModels {
    private GroupedModels() {}

    static UnbakedModel read(Identifier registration, Function<Identifier, Geometry.Groups> loader,
                             JsonObject json, JsonDeserializationContext context) {
        if (json.has("groups") || json.has("hidden") || json.has("transform") || json.has("visibility")) {
            throw new IllegalArgumentException("Group poses, visibility and root transforms require an explicit bake policy");
        }
        var inherited = json.deepCopy();
        inherited.remove("fabric:type");
        inherited.remove("loader");
        inherited.remove("golem:loader");
        var base = context.<UnbakedModel>deserialize(inherited, UnbakedModel.class);
        var skins = new LinkedHashMap<String, List<String>>();
        for (var entry : json.getAsJsonObject("skins").entrySet()) {
            skins.put(entry.getKey(), entry.getValue().getAsJsonArray().asList().stream()
                    .map(value -> value.getAsString()).toList());
        }
        var source = Ids.parse(json.get("model").getAsString());
        // Group positions are centered on X/Z; offset_y supplies the authored vertical origin.
        var offset = new Vector3f(0.5F + number(json, "offset_x"), number(json, "offset_y"),
                0.5F + number(json, "offset_z"));
        var geometry = new GroupsGeometry(new MeshKey(registration, source, loader), skins, offset);
        return new CuboidModel(geometry, base.guiLight(), base.ambientOcclusion(), base.transforms(),
                base.textureSlots(), base.parent());
    }

    private static float number(JsonObject json, String field) {
        return json.has(field) ? json.get(field).getAsFloat() : 0F;
    }

    private record MeshKey(Identifier registration, Identifier source, Function<Identifier, Geometry.Groups> loader)
            implements ModelBaker.SharedOperationKey<Geometry.Groups> {
        @Override public Geometry.Groups compute(ModelBaker baker) { return loader.apply(source); }
    }

    private record GroupsGeometry(MeshKey meshKey, Map<String, List<String>> skins, Vector3f offset)
            implements UnbakedGeometry {
        @Override
        public QuadCollection bake(TextureSlots textures, ModelBaker baker, ModelState state, ModelDebugName name) {
            var mesh = baker.compute(meshKey);
            var output = new QuadCollection.Builder();
            var rotation = state.transformation().getMatrix();
            var interner = baker.interner();
            for (var skin : skins.entrySet()) {
                var material = baker.materials().resolveSlot(textures, skin.getKey(), name);
                var sprite = material.sprite();
                var info = interner.materialInfo(BakedQuad.MaterialInfo.of(material, sprite.transparency(), -1, true, 0));
                var vertices = mesh.parts(skin.getValue().toArray(String[]::new)).vertices();
                var stride = Geometry.FLOATS_PER_VERTEX;
                for (int at = 0; at < vertices.length; at += 3 * stride) {
                    var a = position(vertices, at, offset, rotation);
                    var b = position(vertices, at + stride, offset, rotation);
                    var c = position(vertices, at + 2 * stride, offset, rotation);
                    var normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
                    var face = Direction.getApproximateNearest(normal.x, normal.y, normal.z);
                    if (!state.faceTransformation(face).equals(ModelState.NO_TRANSFORM)) {
                        throw new IllegalArgumentException("Group UV locking requires an explicit authored UV policy: " + name.debugName());
                    }
                    long uv0 = uv(vertices, at, sprite), uv1 = uv(vertices, at + stride, sprite);
                    long uv2 = uv(vertices, at + 2 * stride, sprite);
                    // Native quads carry a triangle by repeating its final vertex.
                    output.addUnculledFace(new BakedQuad(interner.vector(a), interner.vector(b), interner.vector(c),
                            interner.vector(c), uv0, uv1, uv2, uv2, face, info));
                }
            }
            return output.build();
        }
    }

    private static Vector3f position(float[] vertices, int at, Vector3f offset, Matrix4fc rotation) {
        var result = new Vector3f(vertices[at], vertices[at + 1], vertices[at + 2]).add(offset).sub(0.5F, 0.5F, 0.5F);
        rotation.transformPosition(result);
        return result.add(0.5F, 0.5F, 0.5F);
    }

    private static long uv(float[] vertices, int at, TextureAtlasSprite sprite) {
        float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * vertices[at + 3];
        float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * vertices[at + 4];
        return UVPair.pack(u, v);
    }
}
