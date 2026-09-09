#version 460 core

#extension GL_ARB_shader_draw_parameters : require

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

// Same per-instance layout as golem_indirect.vsh — Engine packs the same std140 96-byte struct
// into the source group's SSBO. Crumbling re-uses the existing instance data; only the draw
// pipeline + Sampler0 (destroy_stage texture) + visList contents differ.
struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    // Unused by the crumbling pass but must match AbstractInstancer.STD140_BYTES (112).
    vec4  UVTransform;
};

layout(std140, binding = 0) buffer Instances {
    NtmInstance ntmInstances[];
};

// IndirectCrumblingScratch packs all surviving (mesh, progress) draws into a single visList per
// frame. Each draw's indirect command's baseInstance = visListBaseOffset for its slice. uvec2
// stride matches the regular indirect path (.x unused for crumbling — depth-sort isn't needed
// since the target list is small and tightly clustered around the breaking block).
layout(std430, binding = 1) buffer VisList {
    uvec2 visList[];
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;

void main() {
    uint visIdx = uint(gl_BaseInstanceARB) + uint(gl_InstanceID);
    uint instIdx = visList[visIdx].y;
    NtmInstance inst = ntmInstances[instIdx];

    if (inst.InstanceColor.a <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

    vec4 viewPosition = ModelViewMat * inst.ModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    sphericalVertexDistance = fog_spherical_distance(viewPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPosition.xyz);
    texCoord0 = UV0;
}
