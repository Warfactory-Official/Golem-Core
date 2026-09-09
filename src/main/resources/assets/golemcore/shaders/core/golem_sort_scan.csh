#version 460 core

// Pass 2/3 of GPU radix sort: convert the per-tile bucket histogram from raw counts to
// global exclusive offsets. Dispatched (numDraws, 1, 1) — one workgroup per sortable draw,
// 256 threads = one thread per bucket.
//
// Layout out of histogram pass: histo[((draw * BUCKETS) + bucket) * NumTiles + tile] = count
// of entries in `tile` that fell into `bucket` for this `draw`. After this pass, the same
// slot holds the exclusive prefix offset where (draw, bucket, tile)'s entries should be
// scattered into the destination — that is, the count of all entries in this draw that:
//   - are in any earlier bucket, OR
//   - are in this bucket and any earlier tile.
//
// Three phases:
//   1. Each thread (= one bucket) walks its perDrawTiles slots, in-place exclusive prefix
//      sum, capturing the bucket total in shared mem. perDrawTiles = ceil(live / TILE_SIZE)
//      is uniform across the workgroup, so the loop bound never diverges.
//   2. Cross-bucket exclusive scan over the 256 bucket totals via subgroup-cooperative ops:
//      within-warp subgroupExclusiveAdd, then a single-warp scan over per-warp sums to get
//      the cross-warp carries. Relies on linear localId → (subgroupID, subgroupInvocationID)
//      mapping for 1D workgroups, which every modern desktop driver does.
//   3. Each thread adds its bucket's base offset to every tile slot, completing the global
//      exclusive offset.
//
// Walking only perDrawTiles instead of NumTiles avoids reading stale histogram slots from a
// prior frame for draws whose live count is smaller than the group-wide max — hist matches
// the bound and only writes within [0, perDrawTiles).

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_arithmetic : require

const uint BUCKETS   = 256u;
const uint TILE_SIZE = 256u;
layout(local_size_x = BUCKETS) in;

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

layout(std430, binding = 2) buffer Models { ModelMeta models[]; };
layout(std430, binding = 3) buffer Cmds   { DrawCommand commands[]; };
layout(std430, binding = 4) buffer Histo  { uint histo[]; };

uniform uint NumTiles;

// Phase-2 scan does subgroupExclusiveAdd over warpTotals[invocationID < gl_NumSubgroups],
// which requires gl_NumSubgroups <= gl_SubgroupSize so a single warp can scan all per-warp
// totals in one shot. With local_size_x = 256 and minSubgroupSize >= 16 (NV=32, AMD=64,
// Intel Arc=16) gl_NumSubgroups <= 16 <= gl_SubgroupSize on every modern desktop driver.
// warpTotals[32] is sized to MAX_WARPS for that conservative 16 floor.
shared uint warpTotals[32];

void main() {
    uint draw = gl_WorkGroupID.x;
    if ((models[draw].flags & 1u) == 0u) return;

    uint bucket = gl_LocalInvocationID.x;
    uint base   = (draw * BUCKETS + bucket) * NumTiles;

    uint live = commands[draw].instanceCount;
    uint perDrawTiles = (live + TILE_SIZE - 1u) / TILE_SIZE;

    uint running = 0u;
    for (uint t = 0u; t < perDrawTiles; t++) {
        uint v = histo[base + t];
        histo[base + t] = running;
        running += v;
    }
    uint bucketTotal = running;

    uint warpExcl = subgroupExclusiveAdd(bucketTotal);
    uint warpSum  = subgroupAdd(bucketTotal);
    if (subgroupElect()) warpTotals[gl_SubgroupID] = warpSum;
    barrier();

    if (gl_SubgroupID == 0u) {
        uint v = (gl_SubgroupInvocationID < gl_NumSubgroups) ? warpTotals[gl_SubgroupInvocationID] : 0u;
        v = subgroupExclusiveAdd(v);
        if (gl_SubgroupInvocationID < gl_NumSubgroups) warpTotals[gl_SubgroupInvocationID] = v;
    }
    barrier();

    uint bucketBase = warpExcl + warpTotals[gl_SubgroupID];

    for (uint t = 0u; t < perDrawTiles; t++) {
        histo[base + t] += bucketBase;
    }
}
