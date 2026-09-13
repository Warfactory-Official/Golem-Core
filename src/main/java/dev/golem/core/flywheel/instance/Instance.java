package dev.golem.core.flywheel.instance;

//? if >=26.1 {

import org.joml.Matrix4fc;

// Visual-facing handle to a single GPU-resident instance. Persistent across frames; mutate only
// when the underlying state changes. Backed by InstanceHandle, owned by an Instancer keyed on
// (mesh, material, texture). Call delete() when the visual is removed to release the slot back
// to the Instancer's free list.
public interface Instance {

    void setTransform(Matrix4fc transform);

    void setLight(int packedLight);

    void setOverlay(int packedOverlay);

    void setColor(int argb);

    /**
     * Write the four float components of {@code InstanceColor} directly without ARGB byte
     * quantization. Use for materials that hijack {@code InstanceColor} as raw per-instance
     * data (e.g. {@link dev.golem.core.flywheel.Material#CUTOUT_CLIP_SLAB}'s slide offset
     * in {@code rgb}). Skips the {@link #setVisible} restore-cache update — callers using
     * raw color must re-write each frame, or avoid {@link #setVisible} toggles. Alpha must
     * stay positive on live instances; the dead-instance sentinel is {@code alpha <= 0}.
     */
    void setRawColor(float r, float g, float b, float a);

    void setVisible(boolean visible);

    // Per-instance UV transform: texCoord = vert.uv * (scaleU, scaleV) + (offsetU, offsetV).
    // Identity (0, 0, 1, 1) is the default — non-UV-animated visuals don't need to call this.
    void setUV(float offsetU, float offsetV, float scaleU, float scaleV);

    // Convenience for sprite-sheet animation. Picks the (col, row) cell of an N×M frame grid
    // assuming the texture is stitched in column-major order (X cycles within a row, Y between
    // rows). Equivalent to: setUV(col/cols, row/rows, 1/cols, 1/rows).
    default void setSpriteFrame(int col, int row, int cols, int rows) {
        float scaleU = 1F / cols;
        float scaleV = 1F / rows;
        setUV(col * scaleU, row * scaleV, scaleU, scaleV);
    }

    void delete();

    // Mesh pool handle this instance was allocated against. Exposed so engine-level passes (e.g.
    // the crumbling overlay) can group instances by mesh before issuing draws.
    int meshHandle();
}
//?}
