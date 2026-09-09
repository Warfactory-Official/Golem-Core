#version 460 core

// Pass 1/3 of GPU radix sort: per-tile bucket histogram for one byte of the 32-bit sort key.
// Dispatched (numTiles, numDraws, 1) — one workgroup per (draw, tile). Each thread reads one
// source entry, masks Shift..Shift+7 of its key (.x), and atomicAdds into a 256-bucket shared
// counter. After reduction the workgroup writes its 256 counts into histo[draw][bucket][tile]
// (bucket-major layout = tile-stride NumTiles, see golem_sort_scan.csh for why).
//
// Cull writes only live entries via atomicAdd into commands[draw].instanceCount, so threads
// past `live` never touched a real entry — early-return there.
//
// The dispatch only fires for sortable groups (Java filters on requiresDepthSort) and every
// draw inside such a group has flags=1, so no per-thread flags check is needed.

#extension GL_KHR_shader_subgroup_basic : require

const uint TILE_SIZE = 256u;
const uint BUCKETS   = 256u;

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

// Source slice for this pass — visList on even passes, scratch on odd passes. Java rebinds
// before each dispatch. Each entry is a uvec2: .x carries the key (in the high half), .y is
// the carry-along instance index. Histogram only inspects .x.
layout(std430, binding = 1) buffer Src     { uvec2 src[]; };
layout(std430, binding = 2) buffer Models  { ModelMeta models[]; };
layout(std430, binding = 3) buffer Cmds    { DrawCommand commands[]; };
layout(std430, binding = 4) buffer Histo   { uint histo[]; };

uniform uint Shift;
uniform uint NumTiles;

shared uint bucketCount[BUCKETS];

void main() {
    uint draw  = gl_WorkGroupID.y;
    uint tile  = gl_WorkGroupID.x;
    uint local = gl_LocalInvocationID.x;

    if ((models[draw].flags & 1u) == 0u) return;

    uint live = commands[draw].instanceCount;
    // Skip tiles entirely past this draw's live count — the workgroup launches but nothing
    // else here runs, and scan walks the same per-draw tile budget so unwritten histo slots
    // are never read. tile and live are both uniform across the workgroup.
    uint perDrawTiles = (live + TILE_SIZE - 1u) / TILE_SIZE;
    if (tile >= perDrawTiles) return;

    bucketCount[local] = 0u;
    barrier();

    uint srcIdx = tile * TILE_SIZE + local;
    if (srcIdx < live) {
        uint key = src[models[draw].visListBaseOffset + srcIdx].x;
        uint bucket = (key >> Shift) & 0xFFu;
        atomicAdd(bucketCount[bucket], 1u);
    }
    barrier();

    histo[((draw * BUCKETS) + local) * NumTiles + tile] = bucketCount[local];
}
