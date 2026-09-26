package dev.golem.core.render.gem;

import net.minecraft.core.Direction;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.golem.core.render.Vertexes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Geometry} stated in code rather than loaded from a file.
 *
 * <p>Not every lump this mod draws comes out of an OBJ. A primed charge is a textured cube, an ejected
 * sawblade is two crossed sheets, and both were written as loops emitting vertices straight into the
 * buffer. That is a shape the vanilla pass can draw and the instanced backend cannot see, because the
 * triangles only exist for the duration of the call.
 *
 * <p>Core owns this rather than the consumer for the reason {@link Geometry} does not own a parser: the
 * interleaved {@code x, y, z, u, v, nx, ny, nz} layout is the instanced backend's, and a consumer
 * writing it by hand would be transcribing a detail that is not its to know. Quads are the currency
 * because the vanilla pass takes them directly, while {@link #vertices} splits each into the two
 * triangles the mesh pool wants — the same two forms {@code WavefrontObj} hands over for a loaded group.
 */
public final class Quads {

    private static final int STRIDE = 8;
    private static final int QUAD = 4;

    private Quads() {}

    /** Starts a lump. The name keys its uploaded mesh, so it must be distinct across the mod. */
    public static Builder named(String name) {
        return new Builder(name);
    }

    /** A unit-texture box: every face maps the sub-rectangle of a 16x16 sprite its bounds cover. */
    public static Geometry box(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return box(name, minX, minY, minZ, maxX, maxY, maxZ, 0x3F, 0);
    }

    /**
     * Only the faces in {@code faceMask} ({@code 1 << Direction.ordinal()} bits); {@code rotateMask} faces swap u and
     * v, a quarter turn.
     */
    public static Geometry box(String name, double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                        int faceMask, int rotateMask) {
        Builder builder = named(name);
        float x0 = (float) minX, y0 = (float) minY, z0 = (float) minZ;
        float x1 = (float) maxX, y1 = (float) maxY, z1 = (float) maxZ;
        for (Direction face : Direction.values()) {
            if ((faceMask & (1 << face.ordinal())) == 0) continue;
            boolean rotate = (rotateMask & (1 << face.ordinal())) != 0;
            switch (face) {
                case DOWN -> quad(builder, 0, -1, 0, rotate,
                        x0, y0, z1, x0, z1,
                        x0, y0, z0, x0, z0,
                        x1, y0, z0, x1, z0,
                        x1, y0, z1, x1, z1);
                case UP -> quad(builder, 0, 1, 0, rotate,
                        x1, y1, z1, x1, z1,
                        x1, y1, z0, x1, z0,
                        x0, y1, z0, x0, z0,
                        x0, y1, z1, x0, z1);
                case NORTH -> quad(builder, 0, 0, -1, rotate,
                        x0, y1, z0, 1 - x0, 1 - y1,
                        x1, y1, z0, 1 - x1, 1 - y1,
                        x1, y0, z0, 1 - x1, 1 - y0,
                        x0, y0, z0, 1 - x0, 1 - y0);
                case SOUTH -> quad(builder, 0, 0, 1, rotate,
                        x0, y1, z1, x0, 1 - y1,
                        x0, y0, z1, x0, 1 - y0,
                        x1, y0, z1, x1, 1 - y0,
                        x1, y1, z1, x1, 1 - y1);
                case WEST -> quad(builder, -1, 0, 0, rotate,
                        x0, y1, z1, z1, 1 - y1,
                        x0, y1, z0, z0, 1 - y1,
                        x0, y0, z0, z0, 1 - y0,
                        x0, y0, z1, z1, 1 - y0);
                case EAST -> quad(builder, 1, 0, 0, rotate,
                        x1, y0, z1, 1 - z1, 1 - y0,
                        x1, y0, z0, 1 - z0, 1 - y0,
                        x1, y1, z0, 1 - z0, 1 - y1,
                        x1, y1, z1, 1 - z1, 1 - y1);
            }
        }
        return builder.build();
    }

    /** One face; {@code rotate} swaps each corner's u and v, the quarter turn 1.7's uvRotate applied. */
    private static void quad(Builder builder, float nx, float ny, float nz, boolean rotate,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        builder.normal(nx, ny, nz);
        if (rotate) {
            builder.vertex(ax, ay, az, av, au).vertex(bx, by, bz, bv, bu)
                   .vertex(cx, cy, cz, cv, cu).vertex(dx, dy, dz, dv, du);
        } else {
            builder.vertex(ax, ay, az, au, av).vertex(bx, by, bz, bu, bv)
                   .vertex(cx, cy, cz, cu, cv).vertex(dx, dy, dz, du, dv);
        }
    }

    public static final class Builder {

        private final String name;
        private final List<Float> data = new ArrayList<>();
        private float normalX;
        private float normalY;
        private float normalZ = 1F;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /** The facing every vertex added after this call carries. */
        public Builder normal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
            return this;
        }

        /** Adds one corner. Every four corners, wound counter-clockwise from the front, make a quad. */
        public Builder vertex(float x, float y, float z, float u, float v) {
            data.add(x);
            data.add(y);
            data.add(z);
            data.add(u);
            data.add(v);
            data.add(normalX);
            data.add(normalY);
            data.add(normalZ);
            return this;
        }

        public Geometry build() {
            int corners = data.size() / STRIDE;
            if (corners == 0 || corners % QUAD != 0) {
                throw new IllegalStateException(
                        name + " must be whole quads, but has " + corners + " corners");
            }
            float[] corner = new float[data.size()];
            for (int at = 0; at < corner.length; at++) corner[at] = data.get(at);
            return new Lump(name, corner);
        }
    }

    /**
     * The quads, kept once and read two ways.
     *
     * <p>{@link #vertices} is built on first use rather than up front: a lump is usually drawn through
     * the vanilla pass on five of the six nodes, where the triangulated copy is never asked for.
     */
    private static final class Lump implements Geometry {

        private final String name;
        private final float[] corners;
        private float @Nullable [] vertices;
        private int @Nullable [] indices;

        private Lump(String name, float[] corners) {
            this.name = name;
            this.corners = corners;
        }

        @Override
        public void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color) {
            for (int at = 0; at < corners.length; at += STRIDE) {
                Vertexes.vertex(buffer, pose, corners[at], corners[at + 1], corners[at + 2], color,
                        corners[at + 3], corners[at + 4], overlay, light,
                        corners[at + 5], corners[at + 6], corners[at + 7]);
            }
        }

        @Override
        public float[] vertices() {
            build();
            return vertices;
        }

        @Override
        public int[] indices() {
            build();
            return indices;
        }

        @Override
        public int vertexCount() {
            return corners.length / STRIDE / QUAD * 6;
        }

        @Override
        public String name() {
            return name;
        }

        /** Each quad as the triangles 0-1-2 and 0-2-3, the winding the quad itself was given. */
        private void build() {
            if (vertices != null) return;
            int quads = corners.length / STRIDE / QUAD;
            float[] built = new float[quads * 6 * STRIDE];
            int[] order = {0, 1, 2, 0, 2, 3};
            int out = 0;
            for (int quad = 0; quad < quads; quad++) {
                for (int corner : order) {
                    System.arraycopy(corners, (quad * QUAD + corner) * STRIDE, built, out, STRIDE);
                    out += STRIDE;
                }
            }
            int[] sequence = new int[quads * 6];
            for (int at = 0; at < sequence.length; at++) sequence[at] = at;
            this.indices = sequence;
            this.vertices = built;
        }
    }
}
