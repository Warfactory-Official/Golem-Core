package com.wf.gemrender.gltf;

import com.wf.gemrender.vendor.jgltf.model.NodeModel;
import org.jetbrains.annotations.Nullable;

/**
 * Import-time rig edit: joints added under existing nodes, an unskinned node's vertices weighted onto them. Added
 * joints rest at identity local, so a vertex stays in its owner's space and is skinned without inverse binds.
 */
@FunctionalInterface
public interface RigPatch {

    void patch(Rig rig);

    interface Rig {
        /** First node named {@code name}; null = absent. */
        @Nullable
        NodeModel node(String name);

        /** New joint under {@code parent}, identity rest. */
        NodeModel addJoint(NodeModel parent, String name);

        /** {@code owner}'s unskinned primitives: vertex position (owner space) -> weights. */
        void weigh(NodeModel owner, Weigher weigher);
    }

    @FunctionalInterface
    interface Weigher {
        /**
         * Writes up to 4 {@code joints}/{@code weights} (sum 1) for the vertex at {@code (x, y, z)}; returns the
         * count. The owner itself is a valid joint.
         */
        int weigh(float x, float y, float z, NodeModel[] joints, float[] weights);
    }
}
