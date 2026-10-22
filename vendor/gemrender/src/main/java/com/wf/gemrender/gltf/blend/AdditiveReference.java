package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * The pose an {@link BlendMode#ADDITIVE} layer is measured against: layer contributes
 * {@code sample - reference}. Precomputed once; value equality (part of the pose cache key).
 */
public final class AdditiveReference {
    private final NodeTable table;

    private final float[] state;

    private final int hash;

    private AdditiveReference(NodeTable table, float[] state) {
        this.table = table;
        this.state = state;
        this.hash = System.identityHashCode(table) * 31 + Arrays.hashCode(state);
    }

    /**
     * The model's rest pose: a clip authored as an offset from the bind pose.
     */
    public static AdditiveReference rest(NodeTable table) {
        return new AdditiveReference(table, table.newScratch());
    }

    /**
     * {@code clip} at {@code timeSeconds}; {@code frame(table, clip, 0)} is the usual "first frame" basis.
     */
    public static AdditiveReference frame(NodeTable table, @Nullable GltfAnimation clip, float timeSeconds) {
        float[] state = table.newScratch();
        if (clip != null) {
            clip.apply(timeSeconds, state);
        }
        return new AdditiveReference(table, state);
    }

    float[] state() {
        return state;
    }

    void checkFits(NodeTable evaluated) {
        if (evaluated != table) {
            throw new IllegalArgumentException("additive reference built for another model's node table");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AdditiveReference that && hash == that.hash && table == that.table
                && Arrays.equals(state, that.state);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
