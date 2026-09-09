#version 460 core

// HiZ pyramid first pass: vanilla depth attachment → R32F mip 0. Each invocation samples a
// 2×2 quad of source depth via textureGather and stores the conservative max into the
// destination mip. Conservative max-reduction is the correctness invariant of every HiZ
// downsample: occlusion testing rejects an instance only if its near depth exceeds the
// sampled max, so any false-min would over-cull.
//
// Bound by IndirectDrawManager.dispatchHizBuild before the cull pass:
//   - DepthTex: vanilla MainRenderTarget depth attachment (sampler unit 0)
//   - Mip0:    Golem HiZ pyramid mip 0 (image unit 0)

layout(local_size_x = 8, local_size_y = 8) in;

layout(binding = 0) uniform sampler2D DepthTex;
layout(binding = 0, r32f) uniform restrict writeonly image2D Mip0;

uniform ivec2 Mip0Size;

void main() {
    ivec2 dst = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(dst, Mip0Size))) return;
    // Sample the centre of the 2×2 source quad. textureGather returns the four texels in a
    // fixed (lower-left → counter-clockwise) order; the layout doesn't matter since we max
    // them all together.
    vec2 uv = (vec2(dst) + 0.5) / vec2(Mip0Size);
    vec4 g = textureGather(DepthTex, uv);
    float d = max(max(g.x, g.y), max(g.z, g.w));
    imageStore(Mip0, dst, vec4(d));
}
