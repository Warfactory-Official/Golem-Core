#version 430 core

// Apply pass: copy the model-level survivor counts written by golem_cull.csh into the indirect
// draw commands consumed by glMultiDrawElementsIndirect.

layout(local_size_x = 64) in;

struct ModelMeta {
    uint instanceCount;
    uint visListBaseOffset;
    uint instanceMaxCount;
    uint flags;
    vec4 boundingSphere;
};

struct DrawCommand {
    uint count;
    uint instanceCount;
    uint firstIndex;
    int  baseVertex;
    uint baseInstance;
};

layout(std430, binding = 2) readonly buffer Models {
    ModelMeta models[];
};

layout(std430, binding = 3) buffer Commands {
    DrawCommand commands[];
};

void main() {
    uint draw = gl_GlobalInvocationID.x;
    if (draw >= commands.length()) return;
    commands[draw].instanceCount = models[draw].instanceCount;
}
