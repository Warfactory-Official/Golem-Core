package dev.golem.core.flywheel;

//? if >=26.1 {

import dev.golem.core.flywheel.instance.CrumblingScratch;
import dev.golem.core.flywheel.plan.Plan;
import dev.golem.core.flywheel.visual.DynamicVisual;
import java.util.List;
import net.minecraft.resources.Identifier;

// Backend-facing draw root. Engine builds the shared per-frame state (FrameState, crumbling
// groups), then dispatches to the active backend's DrawManager — which knows how to issue draws
// for its storage layout (UBO + Mojang RenderPass for INSTANCING; SSBO + multidraw + raw GL
// for INDIRECT).
public interface DrawManager {

    Instancer instancer(int meshHandle, Material material, Identifier texture);

    default boolean canDraw(Material material) {
        return MeshPool.vertexBuffer() != null && MeshPool.indexBuffer() != null;
    }

    default Plan<DynamicVisual.Context> frameMaintenancePlan() {
        return Plan.unit();
    }

    void flushAll();

    void drawStage(Material.Stage stage, FrameState state);

    void drawCrumbling(List<CrumblingDraw> draws, FrameState state);

    // INSTANCING-only — INDIRECT will return null until the crumbling path is reimplemented
    // there. Engine doesn't read this; it's exposed for backends that need to coordinate between
    // their own crumbling renderer and the scratch UBO. Will likely move/disappear in a future
    // refactor.
    CrumblingScratch crumblingScratch();

    void close();
}
//?}
