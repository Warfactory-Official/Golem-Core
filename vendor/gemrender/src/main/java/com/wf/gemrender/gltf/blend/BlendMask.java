package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.PoseDriver;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Per-node weight in {@code [0, 1]} a layer is multiplied by; a node's morph weights follow its node.
 *
 * <p>Immutable, value equality: part of the {@link com.wf.gemrender.render.PoseCache} key. Build once
 * per model, never per frame. Bound to the {@link NodeTable} it was built from (identity); {@link #ALL} fits any.
 */
public final class BlendMask {
    /**
     * Every node at full weight, for any model.
     */
    public static final BlendMask ALL = new BlendMask(null, null);

    @Nullable
    private final NodeTable table;

    private final float[] weights;

    private final int hash;

    private BlendMask(@Nullable NodeTable table, float[] weights) {
        this.table = table;
        this.weights = weights;
        this.hash = weights == null ? 0 : System.identityHashCode(table) * 31 + Arrays.hashCode(weights);
    }

    /**
     * The named nodes and everything under them.
     *
     * @throws IllegalArgumentException for a name the table does not have
     */
    public static BlendMask subtree(NodeTable table, String... roots) {
        float[] weights = new float[table.nodeCount()];
        for (String root : roots) {
            weights[require(table, root)] = 1.0f;
        }
        int[] parents = table.parentSlots();
        for (int slot : table.evaluationOrder()) {
            if (parents[slot] >= 0 && weights[parents[slot]] > 0.0f) {
                weights[slot] = 1.0f;
            }
        }
        return new BlendMask(table, weights);
    }

    /**
     * Exactly the named nodes, not their children.
     */
    public static BlendMask nodes(NodeTable table, String... names) {
        float[] weights = new float[table.nodeCount()];
        for (String name : names) {
            weights[require(table, name)] = 1.0f;
        }
        return new BlendMask(table, weights);
    }

    /**
     * Arbitrary per-slot weights, clamped to {@code [0, 1]}.
     */
    public static BlendMask weights(NodeTable table, float[] perSlot) {
        if (perSlot.length != table.nodeCount()) {
            throw new IllegalArgumentException("mask has " + perSlot.length + " weights for "
                    + table.nodeCount() + " nodes");
        }
        float[] weights = new float[perSlot.length];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.min(1.0f, Math.max(0.0f, perSlot[i]));
        }
        return new BlendMask(table, weights);
    }

    /**
     * The nodes {@code clip} writes. A driver that does not report its target ({@link PoseDriver#offset}
     * of -1) makes this {@link #ALL}.
     */
    public static BlendMask drivenBy(NodeTable table, GltfAnimation clip) {
        float[] weights = new float[table.nodeCount()];
        for (PoseDriver driver : clip.drivers()) {
            int slot = table.slotOfOffset(driver.offset());
            if (slot < 0) {
                return ALL;
            }
            weights[slot] = 1.0f;
        }
        return new BlendMask(table, weights);
    }

    private static int require(NodeTable table, String name) {
        int slot = table.slotOfName(name);
        if (slot < 0) {
            throw new IllegalArgumentException("mask names node '" + name + "', which the model does not have");
        }
        return slot;
    }

    /**
     * {@code 1 - w} per node. Base layer under an override layer masked by this: the pair sums to 1 on
     * every node, so the normalized blend reads as "replace where masked".
     */
    public BlendMask complement(NodeTable table) {
        checkFits(table);
        float[] out = new float[table.nodeCount()];
        for (int i = 0; i < out.length; i++) {
            out[i] = 1.0f - weight(i);
        }
        return new BlendMask(table, out);
    }

    public float weight(int slot) {
        return weights == null ? 1.0f : weights[slot];
    }

    public boolean isAll() {
        return weights == null;
    }

    void checkFits(NodeTable evaluated) {
        if (weights != null && evaluated != table) {
            throw new IllegalArgumentException("mask built for another model's node table");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof BlendMask that && hash == that.hash && table == that.table
                && Arrays.equals(weights, that.weights);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
