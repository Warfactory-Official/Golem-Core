#version 460 core

#extension GL_ARB_shader_draw_parameters : require

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// Minimal vertex shader for the Fabulous-mode depth-only pass. Same per-instance lookup as
// golem_indirect.vsh (visList[gl_BaseInstance + gl_InstanceID].y → ntmInstances[instIdx]),
// but emits only gl_Position + texCoord0 — no vertexColor / lightmap / overlay / fog work,
// since the matching golem_indirect_depth.fsh only does alpha discard and writes nothing
// (color writes are masked off at the pipeline level via ColorTargetState.WRITE_NONE).
//
// The skipped output varyings save ~half the per-vertex ALU compared to the color path.
// Per-fragment savings come from the matching minimal fragment shader.

in vec3 Position;
in vec2 UV0;

// Same NtmInstance struct + std140 stride as golem_indirect.vsh; only ModelMat, UVTransform,
// and InstanceColor.a (for the dead-instance sentinel) are read.
struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    vec4  UVTransform;
};

layout(std140, binding = 0) buffer Instances {
    NtmInstance ntmInstances[];
};

layout(std430, binding = 1) buffer VisList {
    uvec2 visList[];
};

out vec2 texCoord0;

void main() {
    uint visIdx = uint(gl_BaseInstanceARB) + uint(gl_InstanceID);
    uint instIdx = visList[visIdx].y;
    NtmInstance inst = ntmInstances[instIdx];

    // Dead-instance sentinel — punch out of clip space so depth isn't written.
    if (inst.InstanceColor.a <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

    gl_Position = ProjMat * ModelViewMat * inst.ModelMat * vec4(Position, 1.0);
    texCoord0 = UV0 * inst.UVTransform.zw + inst.UVTransform.xy;
}
