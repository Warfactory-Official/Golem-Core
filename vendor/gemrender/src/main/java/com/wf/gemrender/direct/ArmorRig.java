package com.wf.gemrender.direct;

import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.gltf.RigPatch;
import net.minecraft.client.model.HumanoidModel;

import java.util.Map;

/**
 * Wearer body with more joints than vanilla's six parts (elbows, knees, ...), installed by the mod that draws that
 * body: {@link GemRenderArmorModel#setRig}. Applies to models declared through
 * {@link com.wf.gemrender.asset.GemRenderModels#armor}.
 */
public interface ArmorRig {

    /** Import: {@code bones} = vanilla part -> node name, {@link GemRenderArmorModel#DEFAULT_BONES} keys. */
    void patch(RigPatch.Rig rig, Map<String, String> bones);

    /**
     * Per draw, after the six part rotations: {@code parts} = this frame's copied wearer parts, {@code slots} =
     * node slot per {@link GemRenderArmorModel#PARTS} entry, {@code -1} = absent.
     */
    void pose(HumanoidModel<?> parts, NodeTable table, float[] state, int[] slots);
}
