package com.wf.gemrender.particle;

import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The unit quad every billboard particle is drawn from, centred on the origin and one block across.
 *
 * <p>{@link #INSTANCE} spans the whole of its texture, which is what a particle with a texture of its own
 * wants. {@link #ofUv} spans a rectangle of one instead, which is what a particle drawn from an <em>atlas</em>
 * wants: a block chip samples the terrain sheet, and the sprite it samples is a fixed window into it that is
 * known when the model is built rather than per-particle. Baking it into the mesh keeps the particle layout
 * untouched: UVs are not per-particle data and should not be paying for a slot in it.
 */
public final class ParticleQuad implements QuadMesh {
    public static final ParticleQuad INSTANCE = new ParticleQuad(0.0f, 0.0f, 1.0f, 1.0f);

    private static final float[] X = {-0.5f, 0.5f, 0.5f, -0.5f};

    private static final float[] Y = {-0.5f, -0.5f, 0.5f, 0.5f};

    private static final Vector4fc BOUNDING_SPHERE = new Vector4f(0.0f, 0.0f, 0.0f, 0.70710678f);

    /** One mesh per sprite, so two chips of the same block share a model and therefore a draw call. */
    private static final Map<Long, ParticleQuad> SPRITES = new ConcurrentHashMap<>();

    private final float[] u;

    private final float[] v;

    private ParticleQuad(float u0, float v0, float u1, float v1) {
        this.u = new float[]{u0, u1, u1, u0};
        this.v = new float[]{v1, v1, v0, v0};
    }

    /**
     * The same quad mapped onto a window of an atlas.
     *
     * @param u0 left, {@code v0} top, {@code u1} right, {@code v1} bottom: the four numbers a
     *           {@code TextureAtlasSprite} reports
     */
    public static ParticleQuad ofUv(float u0, float v0, float u1, float v1) {
        long key = (long) Float.floatToIntBits(u0) << 32 | Float.floatToIntBits(v0) & 0xFFFFFFFFL;
        key = key * 31 + ((long) Float.floatToIntBits(u1) << 32 | Float.floatToIntBits(v1) & 0xFFFFFFFFL);
        return SPRITES.computeIfAbsent(key, k -> new ParticleQuad(u0, v0, u1, v1));
    }

    @Override
    public int vertexCount() {
        return 4;
    }

    @Override
    public void write(MutableVertexList dst) {
        for (int i = 0; i < 4; i++) {
            dst.x(i, X[i]);
            dst.y(i, Y[i]);
            dst.z(i, 0.0f);
            dst.r(i, 1.0f);
            dst.g(i, 1.0f);
            dst.b(i, 1.0f);
            dst.a(i, 1.0f);
            dst.u(i, u[i]);
            dst.v(i, v[i]);
            dst.overlay(i, 0);
            dst.light(i, 0);
            dst.normalX(i, 0.0f);
            dst.normalY(i, 0.0f);
            dst.normalZ(i, 1.0f);
        }
    }

    @Override
    public Vector4fc boundingSphere() {
        return BOUNDING_SPHERE;
    }
}
