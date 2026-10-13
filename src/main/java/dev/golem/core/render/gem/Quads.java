package dev.golem.core.render.gem;

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
 * <p>Not every lump comes out of an OBJ: a primed charge is a textured cube and an ejected sawblade is two
 * crossed sheets. Written as a loop emitting vertices into the buffer, such a shape can be drawn by the
 * vanilla pass but is invisible to the instanced backend, because its triangles only exist for the duration
 * of the call.
 *
 * <p>Quads are the currency because the vanilla pass takes them directly, while {@link #vertices} splits
 * each into the two triangles the mesh pool wants, in the interleaved {@code x, y, z, u, v, nx, ny, nz}
 * layout that belongs to the instanced backend, not to consumers. These are the same two forms
 * {@code WavefrontObj} hands over for a loaded group.
 */
public final class Quads {

    private static final int STRIDE = 8;
    private static final int QUAD = 4;

    private Quads() {}

    /** Starts a lump. The name keys its uploaded mesh, so it must be distinct across the mod. */
    public static Builder named(String name) {
        return new Builder(name);
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
