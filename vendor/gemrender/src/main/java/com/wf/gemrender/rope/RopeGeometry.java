package com.wf.gemrender.rope;

/**
 * The tube's vertices, as plain numbers, with no Flywheel types anywhere near them.
 *
 * <p>Split out of {@link RopeMesh} for the same reason {@code SkinnedCubeGeometry} is split out of
 * {@code SkinnedCubeMesh}: Flywheel is a {@code compileOnly} dependency, so a test that cannot start
 * a game cannot load a class implementing {@code QuadMesh} — and a generated mesh is exactly the kind
 * of thing that has to be checked by arithmetic rather than by eye. Backface culling is on by
 * default, so a wrongly wound face does not vanish, it shows you the inside of the far wall.
 *
 * <p>No vertex here is a position. {@code t} is the parameter along the rope; {@code dirX}/{@code dirY}
 * are the ring's direction on the unit circle, which {@code rope.glsl} rotates into the rope's own
 * frame and multiplies by the radius. {@code capSign} is -1 on the near cap, +1 on the far one and 0
 * on the tube, and rides in the normal's Y — only those three values are ever written, so a
 * normalised-byte normal attribute round-trips it exactly.
 */
public final class RopeGeometry {
    /** t, dirX, dirY, u, v, capSign. */
    public static final int STRIDE = 6;

    public static final int T = 0;
    public static final int DIR_X = 1;
    public static final int DIR_Y = 2;
    public static final int U = 3;
    public static final int V = 4;
    public static final int CAP_SIGN = 5;

    /** Enough that a rope bent double has no visible flats, cheap enough to be the default. */
    public static final int DEFAULT_RINGS = 16;

    /** Four is what a vanilla lead looks like, and it is the cheapest tube that is not a ribbon. */
    public static final int DEFAULT_SIDES = 4;

    private RopeGeometry() {
    }

    public static int vertexCount(int rings, int sides, boolean capped) {
        return 4 * sides * (rings + (capped ? 2 : 0));
    }

    /**
     * Every vertex of the tube, in draw order, {@link #STRIDE} floats each.
     */
    public static float[] vertices(int rings, int sides, boolean capped) {
        if (rings < 1 || sides < 3) {
            throw new IllegalArgumentException("a rope needs at least one ring and three sides");
        }

        float[] out = new float[vertexCount(rings, sides, capped) * STRIDE];
        int v = 0;

        if (capped) {
            v = cap(out, v, sides, 0.0f, -1.0f);
        }
        for (int ring = 0; ring < rings; ring++) {
            v = band(out, v, sides, (float) ring / rings, (float) (ring + 1) / rings);
        }
        if (capped) {
            v = cap(out, v, sides, 1.0f, 1.0f);
        }
        return out;
    }

    /** One ring of quads from {@code t0} to {@code t1}, wound so the faces point out of the tube. */
    private static int band(float[] out, int v, int sides, float t0, float t1) {
        for (int side = 0; side < sides; side++) {
            float c0 = cos(side, sides);
            float s0 = sin(side, sides);
            float c1 = cos(side + 1, sides);
            float s1 = sin(side + 1, sides);

            float u0 = (float) side / sides;
            float u1 = (float) (side + 1) / sides;

            v = vertex(out, v, t0, c0, s0, u0, t0, 0.0f);
            v = vertex(out, v, t1, c0, s0, u0, t1, 0.0f);
            v = vertex(out, v, t1, c1, s1, u1, t1, 0.0f);
            v = vertex(out, v, t0, c1, s1, u1, t0, 0.0f);
        }
        return v;
    }

    /**
     * A disc closing one end: a fan of quads from the axis out to the rim, the outer corner doubled so
     * a triangle fits in a quad mesh. The two ends wind opposite ways, or the far cap faces back down
     * the rope instead of out of it — which looks like nothing at all until you fly past the end.
     */
    private static int cap(float[] out, int v, int sides, float t, float facing) {
        for (int side = 0; side < sides; side++) {
            float c0 = cos(side, sides);
            float s0 = sin(side, sides);
            float c1 = cos(side + 1, sides);
            float s1 = sin(side + 1, sides);

            float u0 = (float) side / sides;
            float u1 = (float) (side + 1) / sides;

            v = vertex(out, v, t, 0.0f, 0.0f, 0.5f, t, facing);
            if (facing < 0.0f) {
                v = vertex(out, v, t, c0, s0, u0, t, facing);
                v = vertex(out, v, t, c1, s1, u1, t, facing);
                v = vertex(out, v, t, c1, s1, u1, t, facing);
            } else {
                v = vertex(out, v, t, c1, s1, u1, t, facing);
                v = vertex(out, v, t, c0, s0, u0, t, facing);
                v = vertex(out, v, t, c0, s0, u0, t, facing);
            }
        }
        return v;
    }

    private static int vertex(float[] out, int v, float t, float dirX, float dirY,
                              float u, float vCoord, float capSign) {
        int base = v * STRIDE;
        out[base + T] = t;
        out[base + DIR_X] = dirX;
        out[base + DIR_Y] = dirY;
        out[base + U] = u;
        out[base + V] = vCoord;
        out[base + CAP_SIGN] = capSign;
        return v + 1;
    }

    private static float cos(int side, int sides) {
        return (float) Math.cos(2.0 * Math.PI * side / sides);
    }

    private static float sin(int side, int sides) {
        return (float) Math.sin(2.0 * Math.PI * side / sides);
    }
}
