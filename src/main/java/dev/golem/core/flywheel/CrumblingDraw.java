package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.instance.Instance;
import java.util.List;

// One (mesh, destroy_stage progress) bucket of instances to overlay with the crumbling
// material. Engine builds these by walking the level renderer's BlockBreakingRenderState list,
// resolving each mined position to its visual (going through BlockDummyable.findCore for
// multiblock dummies), and asking the visual to enumerate its instances via
// collectCrumblingInstances. The DrawManager is responsible for the actual GPU dispatch.
public record CrumblingDraw(int meshHandle, int progress, List<Instance> instances) {
}
//?}
