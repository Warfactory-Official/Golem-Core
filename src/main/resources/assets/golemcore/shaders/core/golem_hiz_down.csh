#version 460 core

// HiZ pyramid downsample pass: mip k → mip k+1. Each invocation reads a 2×2 quad from the
// source mip and stores the max into the destination. Dispatched once per (Src, Dst) pair
// in IndirectDrawManager.dispatchHizBuild. Bindings rotate per pass — no fixed (k → k+1)
// hard-wiring.

layout(local_size_x = 8, local_size_y = 8) in;

layout(binding = 0, r32f) uniform restrict readonly image2D Src;
layout(binding = 1, r32f) uniform restrict writeonly image2D Dst;

uniform ivec2 DstSize;

void main() {
    ivec2 dst = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(dst, DstSize))) return;
    ivec2 src = dst * 2;
    float a = imageLoad(Src, src + ivec2(0, 0)).r;
    float b = imageLoad(Src, src + ivec2(1, 0)).r;
    float c = imageLoad(Src, src + ivec2(0, 1)).r;
    float d = imageLoad(Src, src + ivec2(1, 1)).r;
    imageStore(Dst, dst, vec4(max(max(a, b), max(c, d))));
}
