package dev.golem.core.render.gem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jspecify.annotations.Nullable;

/**
 * One rigid lump of triangles a {@linkplain Gem.Part part} draws, supplied by the consumer. Core does
 * not parse meshes; it needs the same lump expressed twice, once for each backend, {@link #draw} for
 * the vanilla submit pass, {@link #vertices}/{@link #indices} as a resident mesh for the instanced one.
 *
 * <p>Both must describe the same surface. In particular a single-sided mesh drawn with reversed faces
 * must <em>duplicate</em> those reversed triangles into {@link #vertices} rather than rely on a no-cull
 * material: no-cull keeps the front face's normal on the back face, reversed geometry flips it.
 *
 * <p>Implementations are expected to be immutable and shared: the instanced backend uploads
 * {@link #vertices} once per distinct instance of this interface.
 */
public interface Geometry {

    /** Floats per vertex in {@link #vertices}: three positions, two texture coordinates, three normals. */
    int FLOATS_PER_VERTEX = 8;

    /**
     * Writes this lump's triangles into {@code buffer}.
     *
     * @param color ARGB vertex tint, or {@code -1} for the untinted default
     */
    void draw(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int color);

    /**
     * The same triangles interleaved as {@code x, y, z, u, v, nx, ny, nz} per vertex, the layout the
     * instanced backend's mesh pool takes.
     */
    float[] vertices();

    /** Triangle indices into {@link #vertices}. */
    int[] indices();

    /** How many vertices {@link #vertices} holds; it is a flat array, so it cannot be derived safely. */
    int vertexCount();

    /** A stable name for this lump, used to key the uploaded mesh and to name it in failures. */
    String name();

    /**
     * This lump's axis-aligned bounds in mesh space, as {@code {{minX, minY, minZ}, {maxX, maxY, maxZ}}},
     * or {@code null} when it holds no vertices.
     *
     * <p>The default walks {@link #vertices}; an implementation that knows the numbers should override it.
     */
    default float @Nullable [][] bounds() {
        float[] data = vertices();
        if (data.length < FLOATS_PER_VERTEX) return null;
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int at = 0; at + FLOATS_PER_VERTEX <= data.length; at += FLOATS_PER_VERTEX) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], data[at + axis]);
                max[axis] = Math.max(max[axis], data[at + axis]);
            }
        }
        return new float[][] {min, max};
    }
}
