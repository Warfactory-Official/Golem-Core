// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

// A named coordinate space that child visuals can compose their transforms against. In Flywheel
// this is the unit of batched/instanced hierarchies; here it is simply a cached parent matrix
// that a visual multiplies into its own pose before submitting. Useful for e.g. a missile silo
// visual that spawns sub-part visuals: the silo owns the embedding, the parts read it.
public final class VisualEmbedding {

    private final Matrix4f pose = new Matrix4f();

    public VisualEmbedding identity() {
        pose.identity();
        return this;
    }

    public VisualEmbedding set(Matrix4fc source) {
        pose.set(source);
        return this;
    }

    public Matrix4f pose() {
        return pose;
    }

    public Matrix4fc poseReadOnly() {
        return pose;
    }
}
//?}
