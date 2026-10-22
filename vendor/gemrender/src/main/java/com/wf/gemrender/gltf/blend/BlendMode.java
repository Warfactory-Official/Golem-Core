package com.wf.gemrender.gltf.blend;

public enum BlendMode {
    /**
     * Weighted average with the other override layers, normalized per node; rest fills a total below 1.
     */
    OVERRIDE,
    /**
     * Offset from an {@link AdditiveReference}, scaled by weight, applied after every override layer in
     * layer order. Rotation composes on the right (bone-local), scale multiplies, translation and morph
     * weights add.
     */
    ADDITIVE
}
