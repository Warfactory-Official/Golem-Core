#version 460 core

// Single-workgroup bitonic sort fast-path. Replaces the 2-pass radix (hist→scan→scatter ×2)
// when every draw in the group has live ≤ TILE_SIZE: one dispatch per draw, no scratch
// buffer, no histogram, no cross-pass barriers.
//
// Dispatched (1, numDraws, 1). Each workgroup loads its draw's visList slice into shared
// memory (padding past `live` with a sentinel uvec2 whose .x = 0xFFFFFFFFu so it sorts to
// the end), runs an in-shared-mem bitonic sort keyed on .x, then writes the first `live`
// entries back. With the depth key in .x's high half, ascending-uint sort on .x =
// descending-distance² = farthest-first, same convention as the radix path. The .y payload
// (32-bit instance index) rides along on each compare-exchange.
//
// Padding entries land at the end of the ascending sort. The indirect command's
// instanceCount = live, so the draw never reads them.
//
// Bitonic over TILE_SIZE elements: log2(N) outer stages × up to log2(N) inner sub-stages =
// 36 barrier-bounded compare-exchange phases for N=256. Half the threads swap per phase.
// Standard textbook bitonic; no subgroup ops required.

const uint TILE_SIZE = 256u;

layout(local_size_x = TILE_SIZE) in;

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

layout(std430, binding = 1) buffer VisList { uvec2 visList[]; };
layout(std430, binding = 2) buffer Models  { ModelMeta models[]; };
layout(std430, binding = 3) buffer Cmds    { DrawCommand commands[]; };

shared uvec2 sdata[TILE_SIZE];

void main() {
    uint draw  = gl_WorkGroupID.y;
    uint local = gl_LocalInvocationID.x;

    if ((models[draw].flags & 1u) == 0u) return;

    uint live = commands[draw].instanceCount;
    uint base = models[draw].visListBaseOffset;

    sdata[local] = (local < live) ? visList[base + local] : uvec2(0xFFFFFFFFu, 0u);
    barrier();

    for (uint k = 2u; k <= TILE_SIZE; k <<= 1u) {
        for (uint j = k >> 1u; j > 0u; j >>= 1u) {
            uint ixj = local ^ j;
            if (ixj > local) {
                bool ascending = ((local & k) == 0u);
                uvec2 a = sdata[local];
                uvec2 b = sdata[ixj];
                if ((ascending && a.x > b.x) || (!ascending && a.x < b.x)) {
                    sdata[local] = b;
                    sdata[ixj]   = a;
                }
            }
            barrier();
        }
    }

    if (local < live) {
        visList[base + local] = sdata[local];
    }
}
