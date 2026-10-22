package dev.golem.core.gemrender;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.FogShader;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.MaterialShaders;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.FogShaders;
import dev.engine_room.flywheel.lib.material.SimpleFogShader;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.material.SimpleMaterialShaders;
import dev.engine_room.flywheel.lib.model.SingleMeshModel;
import dev.golem.core.Ids;
import dev.golem.core.render.gem.Geometry;
import dev.golem.core.render.gem.Surface;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/**
 * A gem {@link Geometry} on a {@link Surface} as one shared Flywheel {@link Model} (an instancer is keyed on it), cached by
 * the lump's name so a resource reload replaces the stale model.
 */
final class GemModels {

    private static final Map<Key, Built> MODELS = new ConcurrentHashMap<>();
    private static final Map<Surface, Material> MATERIALS = new ConcurrentHashMap<>();

    /** GemRender 0.1.14's 26.1 Flywheel keeps 1.21's smoothstep fog; 26.1 vanilla ramps linearly. */
    private static final FogShader FOG = vanillaFog();

    /** 26.1's two-sided entity pipelines light a back face with the negated light; Flywheel lights by normal. */
    private static final MaterialShaders PER_FACE = new SimpleMaterialShaders(
            Ids.of("flywheel", "material/default.vert"), Ids.of("golemcore", "material/per_face.frag"));

    private GemModels() {}

    static Model of(Geometry geometry, Surface surface) {
        // compute, not get-then-put: visuals are built on Flywheel's worker threads.
        return MODELS.compute(new Key(geometry.name(), surface), (key, held) ->
                held != null && held.geometry() == geometry
                        ? held
                        : new Built(geometry, new SingleMeshModel(new GeometryMesh(geometry), materialOf(surface))))
                .model();
    }

    static int modelCount() {
        return MODELS.size();
    }

    /** Each blend spelled as the state of the vanilla render type {@link Surface#renderType} picks. */
    private static Material materialOf(Surface surface) {
        return MATERIALS.computeIfAbsent(surface, skin -> {
            SimpleMaterial.Builder builder = SimpleMaterial.builder()
                    .texture(Ids.parse(skin.texture()))
                    .fog(FOG)
                    .mipmap(false);
            return switch (skin.blend()) {
                case SOLID -> builder.cutout(CutoutShaders.OFF).build();
                case CUTOUT, CUTOUT_CULL -> builder.cutout(CutoutShaders.ONE_TENTH).build();
                case CUTOUT_NO_CULL -> perFace(builder.cutout(CutoutShaders.ONE_TENTH).backfaceCulling(false));
                case TRANSLUCENT -> perFace(builder.transparency(Transparency.TRANSLUCENT)
                        .cutout(CutoutShaders.ONE_TENTH)
                        .backfaceCulling(false));
                case TRANSLUCENT_EMISSIVE -> perFace(builder.transparency(Transparency.TRANSLUCENT)
                        .cutout(CutoutShaders.ONE_TENTH)
                        .useLight(false)
                        .writeMask(WriteMask.COLOR)
                        .backfaceCulling(false));
                case PLASMA_GLOW -> plasmaGlow(builder);
            };
        });
    }

    private static Material perFace(SimpleMaterial.Builder builder) {
        //? if >=26.1 {
        if (!GemRender.UNFIXED) return builder.build();
        return builder.shaders(PER_FACE).cardinalLightingMode(CardinalLightingMode.OFF).build();
        //?} else {
        /*return builder.build();
        *///?}
    }

    private static FogShader vanillaFog() {
        //? if >=26.1 {
        if (!GemRender.UNFIXED) return FogShaders.LINEAR;
        return new SimpleFogShader(Ids.of("golemcore", "fog/linear.glsl"));
        //?} else {
        /*return FogShaders.LINEAR;
        *///?}
    }

    /** SRC_ALPHA/ONE, unculled, colour only and unlit, as {@code PlasmaGlowRenderType} draws on this node. */
    private static Material plasmaGlow(SimpleMaterial.Builder builder) {
        builder.transparency(Transparency.LIGHTNING)
                .useLight(false)
                .useOverlay(false)
                .writeMask(WriteMask.COLOR)
                .backfaceCulling(false);
        //? if >=26.1 {
        return builder.cutout(CutoutShaders.OFF).cardinalLightingMode(CardinalLightingMode.OFF).build();
        //?} else {
        /*return builder.cutout(CutoutShaders.ONE_TENTH).cardinalLightingMode(CardinalLightingMode.ENTITY).build();
        *///?}
    }

    /** Geometry's eight floats per vertex: position, uv, normal. */
    private record GeometryMesh(Geometry geometry) implements Mesh {

        private static final int STRIDE = 8;

        /** Every mesh is unindexed triangle soup; one shared object, as IndexPool keys runs on identity. */
        private static final IndexSequence SEQUENTIAL = (ptr, count) -> {
            for (int index = 0; index < count; index++) {
                MemoryUtil.memPutInt(ptr + index * 4L, index);
            }
        };

        @Override
        public int vertexCount() {
            return geometry.vertexCount();
        }

        @Override
        public void write(MutableVertexList list) {
            float[] vertices = geometry.vertices();
            for (int vertex = 0; vertex < geometry.vertexCount(); vertex++) {
                int at = vertex * STRIDE;
                list.x(vertex, vertices[at]);
                list.y(vertex, vertices[at + 1]);
                list.z(vertex, vertices[at + 2]);
                list.u(vertex, vertices[at + 3]);
                list.v(vertex, vertices[at + 4]);
                list.normalX(vertex, vertices[at + 5]);
                list.normalY(vertex, vertices[at + 6]);
                list.normalZ(vertex, vertices[at + 7]);
                list.r(vertex, 1F);
                list.g(vertex, 1F);
                list.b(vertex, 1F);
                list.a(vertex, 1F);
                list.light(vertex, 0);
                list.overlay(vertex, OverlayTexture.NO_OVERLAY);
            }
        }

        @Override
        public IndexSequence indexSequence() {
            return SEQUENTIAL;
        }

        @Override
        public int indexCount() {
            return geometry.indices().length;
        }

        /** Around the lump's own vertices: a machine reaches well past its block, and Flywheel culls on this. */
        @Override
        public Vector4fc boundingSphere() {
            float[] vertices = geometry.vertices();
            int count = geometry.vertexCount();
            if (count == 0) return new Vector4f(0F, 0F, 0F, 0F);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int vertex = 0; vertex < count; vertex++) {
                int at = vertex * STRIDE;
                minX = Math.min(minX, vertices[at]);
                maxX = Math.max(maxX, vertices[at]);
                minY = Math.min(minY, vertices[at + 1]);
                maxY = Math.max(maxY, vertices[at + 1]);
                minZ = Math.min(minZ, vertices[at + 2]);
                maxZ = Math.max(maxZ, vertices[at + 2]);
            }
            float centreX = (minX + maxX) * 0.5F;
            float centreY = (minY + maxY) * 0.5F;
            float centreZ = (minZ + maxZ) * 0.5F;
            float radius = 0F;
            for (int vertex = 0; vertex < count; vertex++) {
                int at = vertex * STRIDE;
                float dx = vertices[at] - centreX;
                float dy = vertices[at + 1] - centreY;
                float dz = vertices[at + 2] - centreZ;
                radius = Math.max(radius, dx * dx + dy * dy + dz * dz);
            }
            return new Vector4f(centreX, centreY, centreZ, (float) Math.sqrt(radius));
        }
    }

    private record Key(String name, Surface surface) {}

    private record Built(Geometry geometry, Model model) {}
}
