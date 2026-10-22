package com.wf.gemrender.rope;

import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.model.QuadMesh;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * A tube of {@code rings} segments and {@code sides} faces, in curve space rather than world space.
 *
 * <p>The numbers are {@link RopeGeometry}'s; this only hands them to Flywheel. One mesh serves every
 * rope of every length and every shape, which is the whole reason a hundred ropes are one draw.
 */
public final class RopeMesh implements QuadMesh {
    public static final int DEFAULT_RINGS = RopeGeometry.DEFAULT_RINGS;

    public static final int DEFAULT_SIDES = RopeGeometry.DEFAULT_SIDES;

    private static final Vector4fc BOUNDING_SPHERE = new Vector4f(0.0f, 0.0f, 0.0f, 1.0f);

    private final int rings;

    private final int sides;

    private final float[] vertices;

    public RopeMesh(int rings, int sides, boolean capped) {
        this.rings = rings;
        this.sides = sides;
        this.vertices = RopeGeometry.vertices(rings, sides, capped);
    }

    public static RopeMesh of(int rings, int sides) {
        return new RopeMesh(rings, sides, true);
    }

    public int rings() {
        return rings;
    }

    public int sides() {
        return sides;
    }

    @Override
    public int vertexCount() {
        return vertices.length / RopeGeometry.STRIDE;
    }

    @Override
    public void write(MutableVertexList dst) {
        for (int i = 0; i < vertexCount(); i++) {
            int base = i * RopeGeometry.STRIDE;

            dst.x(i, vertices[base + RopeGeometry.T]);
            dst.y(i, vertices[base + RopeGeometry.DIR_X]);
            dst.z(i, vertices[base + RopeGeometry.DIR_Y]);

            dst.r(i, 1.0f);
            dst.g(i, 1.0f);
            dst.b(i, 1.0f);
            dst.a(i, 1.0f);

            dst.u(i, vertices[base + RopeGeometry.U]);
            dst.v(i, vertices[base + RopeGeometry.V]);

            dst.overlay(i, 0);
            dst.light(i, 0);

            // The real normal is the ring direction rotated into the rope's frame, which only the
            // shader knows; Y carries the cap sign, which only the mesh knows.
            dst.normalX(i, vertices[base + RopeGeometry.DIR_X]);
            dst.normalY(i, vertices[base + RopeGeometry.CAP_SIGN]);
            dst.normalZ(i, vertices[base + RopeGeometry.DIR_Y]);
        }
    }

    /**
     * Meaningless here — every vertex is a curve parameter — so the instance carries the real one and
     * {@code cull/rope.glsl} ignores this, exactly as the skinned path does.
     */
    @Override
    public Vector4fc boundingSphere() {
        return BOUNDING_SPHERE;
    }
}
