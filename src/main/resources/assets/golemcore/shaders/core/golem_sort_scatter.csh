#version 460 core

// Pass 3/3 of GPU radix sort: stable scatter of source entries to their sorted destinations
// using the global exclusive offsets computed by golem_sort_scan.csh. Same dispatch shape as
// histogram — (numTiles, numDraws, 1) workgroups, TILE_SIZE threads each.
//
// LSD radix requires every pass to be stable: same-bucket entries must preserve their
// pre-pass order so prior-pass orderings survive into later passes. The naive
// `atomicAdd(sharedDest[bucket], 1u)` approach is NOT stable — atomic order is GPU-warp-
// execution order, which differs from threads' localID order, so same-bucket entries get
// shuffled within a tile every frame → flicker on translucent particle clusters.
//
// This shader uses a stable scatter:
//   1. Within each warp, compute warp-local rank for "threads with same bucket as me, with
//      smaller subgroupInvocationID" via subgroupBallot match_any (8-bit decomposition).
//   2. First alive lane per (warp, bucket) writes the warp's per-bucket count into
//      warpBucketCount[warp][bucket].
//   3. Each thread (one per bucket via local=bucket) does an exclusive prefix sum across
//      warps for its bucket → warpBucketCount[w][bucket] becomes "entries of this bucket in
//      warps 0..w-1 within this tile".
//   4. Each alive thread's destination = sharedDest[bucket] + warpBucketCount[my_warp][bucket]
//      + warp_local_rank. Stable in localID order (= warp-id then lane-id).
//
// TILE_SIZE = 256 caps subgroup count at 256/minSubgroupSize. Modern desktop drivers report
// minSubgroupSize ≥ 16 (NV=32, AMD=64, Intel Arc=16) so MAX_WARPS=32 covers all of them.

#extension GL_KHR_shader_subgroup_basic : require
#extension GL_KHR_shader_subgroup_ballot : require
#extension GL_KHR_shader_subgroup_arithmetic : require

const uint TILE_SIZE = 256u;
const uint BUCKETS   = 256u;
const uint MAX_WARPS = 32u;

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

layout(std430, binding = 1) buffer Src     { uvec2 src[]; };
layout(std430, binding = 5) buffer Dst     { uvec2 dst[]; };
layout(std430, binding = 2) buffer Models  { ModelMeta models[]; };
layout(std430, binding = 3) buffer Cmds    { DrawCommand commands[]; };
layout(std430, binding = 4) buffer Histo   { uint histo[]; };

uniform uint Shift;
uniform uint NumTiles;

shared uint sharedDest[BUCKETS];
shared uint warpBucketCount[MAX_WARPS][BUCKETS];

void main() {
    uint draw  = gl_WorkGroupID.y;
    uint tile  = gl_WorkGroupID.x;
    uint local = gl_LocalInvocationID.x;

    if ((models[draw].flags & 1u) == 0u) return;

    // Skip tiles entirely past this draw's live count — uniform across the workgroup, so the
    // early-return doesn't diverge with the barriers below. Scan only filled histo slots in
    // [0, perDrawTiles), so reading farther would pick up stale data.
    uint live = commands[draw].instanceCount;
    uint perDrawTiles = (live + TILE_SIZE - 1u) / TILE_SIZE;
    if (tile >= perDrawTiles) return;

    sharedDest[local] = histo[((draw * BUCKETS) + local) * NumTiles + tile];

    // Clear warpBucketCount only for rows actually used this dispatch. The prefix-sum loop
    // below only walks gl_NumSubgroups rows, so unused rows are never read — no need to
    // zero them. gl_NumSubgroups is uniform across the workgroup; the bound stays uniform.
    // On NV (subgroupSize=32) this is 8 rows instead of 32; on AMD (subgroupSize=64) it's 4.
    for (uint w = 0u; w < gl_NumSubgroups; w++) {
        warpBucketCount[w][local] = 0u;
    }
    barrier();

    uint srcIdx = tile * TILE_SIZE + local;
    bool alive = srcIdx < live;

    uvec2 entry = uvec2(0u, 0u);
    uint bucket = 0u;
    if (alive) {
        entry  = src[models[draw].visListBaseOffset + srcIdx];
        bucket = (entry.x >> Shift) & 0xFFu;
    }

    // Compute "warpmates with same bucket as me" mask via 8-bit decomposition: AND together
    // 8 ballots, one per bit of `bucket`, choosing ballot or its complement based on whether
    // my bit is 1 or 0. Inalive lanes are excluded from the start (subgroupBallot(alive)).
    uvec4 mask = subgroupBallot(alive);
    for (uint b = 0u; b < 8u; b++) {
        bool bit = (bucket & (1u << b)) != 0u;
        uvec4 ballot = subgroupBallot(alive && bit);
        mask = bit ? (mask & ballot) : (mask & ~ballot);
    }

    uvec4 ltMask = uvec4(
        mask.x & gl_SubgroupLtMask.x,
        mask.y & gl_SubgroupLtMask.y,
        mask.z & gl_SubgroupLtMask.z,
        mask.w & gl_SubgroupLtMask.w
    );
    uint warpRank  = subgroupBallotBitCount(ltMask);
    uint warpCount = subgroupBallotBitCount(mask);

    // First alive lane per (warp, bucket) writes warpCount. "First" = lane whose
    // subgroupInvocationID equals the lowest set bit in mask.
    if (alive && subgroupBallotFindLSB(mask) == gl_SubgroupInvocationID) {
        warpBucketCount[gl_SubgroupID][bucket] = warpCount;
    }
    barrier();

    // Per-bucket exclusive prefix sum across warps. 256 threads, each thread handles one
    // bucket = its localID. Sequential walk over up to MAX_WARPS rows.
    uint cumulative = 0u;
    for (uint w = 0u; w < gl_NumSubgroups; w++) {
        uint c = warpBucketCount[w][local];
        warpBucketCount[w][local] = cumulative;
        cumulative += c;
    }
    barrier();

    if (!alive) return;

    uint warpOffset = warpBucketCount[gl_SubgroupID][bucket];
    uint slot = sharedDest[bucket] + warpOffset + warpRank;
    dst[models[draw].visListBaseOffset + slot] = entry;
}
