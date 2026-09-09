package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;

// Shared per-frame state computed once by Engine and handed to the active DrawManager.
// - modelView: cameraRotation * Translate(renderOrigin - cameraPos) — render-origin-relative
//   view; instance ModelMats are composed on top.
// - viewProjection: ProjMat * modelView. Used by the INDIRECT cull pass to extract frustum
//   planes in render-origin space.
// - cameraPosRO: camera position in render-origin space (cameraPos - renderOrigin). Used by
//   the INDIRECT cull pass to compute per-instance distSq as the sortable depth key.
// - transforms: slice returned by RenderSystem.getDynamicUniforms().writeTransform(); must be
//   re-set per render pass via setUniform("DynamicTransforms", ...).
// - shaderLights: slice of the per-VisualizationManager ShaderLightBuffer — vec4 LightCount
//   header + MAX_LIGHTS × 32-byte per-light records. Bound via setUniform("ShaderLights", ...)
//   inside each backend's RenderPass; fragment shaders accumulate quadratic-falloff additive
//   contribution before fog.
public record FrameState(Minecraft minecraft, Matrix4fc modelView, Matrix4fc viewProjection,
                         Vector3fc cameraPosRO, GpuBufferSlice transforms,
                         GpuBufferSlice shaderLights) {
}
//?}
