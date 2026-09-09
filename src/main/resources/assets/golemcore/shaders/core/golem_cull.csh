#version 430 core

// Frustum/HiZ culling pass for the INDIRECT backend. Dispatched once per active global
// instance page in the stage group; each workgroup covers exactly one 32-instance page and
// uses the page descriptor's valid-bit mask to skip dead or explicitly-invisible slots.
//
// Visible instances atomically append their global instance index into the draw's visList
// slice and increment models[modelIndex].instanceCount. A separate apply pass copies those
// model survivor counts into the indirect command buffer before draw submission.

layout(local_size_x = 32) in;

struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    vec4  UVTransform;
};

struct ModelMeta {
    uint instanceCount;
    uint visListBaseOffset;
    uint instanceMaxCount;
    uint flags;            // bit 0: sortable mode
    vec4 boundingSphere;
};

layout(std140, binding = 0) buffer Instances {
    NtmInstance instances[];
};

layout(std430, binding = 1) buffer VisList {
    uvec2 visList[];
};

layout(std430, binding = 2) buffer Models {
    ModelMeta models[];
};

// pageDescriptors[page] = uvec2(modelIndex, validBits)
layout(std430, binding = 6) readonly buffer PageDescriptors {
    uvec2 pageDescriptors[];
};

uniform vec4 FrustumPlanes[6];
uniform vec3 CameraPosRO;
uniform mat4 ModelView;
uniform vec4 ProjectionInfo;
uniform vec4 PyramidInfo;
uniform uint HizEnabled;

layout(binding = 0) uniform sampler2D DepthPyramid;

bool projectSphere(vec3 viewC, float r, float znear, float P00, float P11, out vec4 aabb) {
    if (viewC.z + r > -znear) return false;

    vec3 cr = viewC * r;
    float czr2 = viewC.z * viewC.z - r * r;

    float vx = sqrt(viewC.x * viewC.x + czr2);
    float minx = (vx * viewC.x - cr.z) / (vx * viewC.z + cr.x);
    float maxx = (vx * viewC.x + cr.z) / (vx * viewC.z - cr.x);

    float vy = sqrt(viewC.y * viewC.y + czr2);
    float miny = (vy * viewC.y - cr.z) / (vy * viewC.z + cr.y);
    float maxy = (vy * viewC.y + cr.z) / (vy * viewC.z - cr.y);

    aabb = vec4(minx * P00, miny * P11, maxx * P00, maxy * P11);
    aabb = aabb.xwzy * vec4(-0.5, -0.5, -0.5, -0.5) + vec4(0.5);
    return true;
}

bool occluded(vec3 viewC, float r) {
    float znear = ProjectionInfo.x;
    vec4 aabb;
    if (!projectSphere(viewC, r, znear, ProjectionInfo.y, ProjectionInfo.z, aabb)) return false;

    float w = (aabb.z - aabb.x) * PyramidInfo.x;
    float h = (aabb.w - aabb.y) * PyramidInfo.y;
    int level = clamp(int(ceil(log2(max(w, h)))), 0, int(PyramidInfo.z));

    ivec2 levelSize = textureSize(DepthPyramid, level);
    ivec4 levelSizePair = ivec4(levelSize, levelSize);
    ivec4 bounds = ivec4(aabb * vec4(levelSizePair));
    bounds = clamp(bounds, ivec4(0), levelSizePair - ivec4(1));

    float d00 = texelFetch(DepthPyramid, bounds.xy, level).r;
    float d10 = texelFetch(DepthPyramid, bounds.zy, level).r;
    float d01 = texelFetch(DepthPyramid, bounds.xw, level).r;
    float d11 = texelFetch(DepthPyramid, bounds.zw, level).r;
    float depth = max(max(d00, d01), max(d10, d11));

    float depthSphere = 1.0 + znear / (viewC.z + r);
    return depthSphere > depth;
}

void main() {
    uint page = gl_WorkGroupID.x;
    if (page >= pageDescriptors.length()) return;

    uvec2 descriptor = pageDescriptors[page];
    uint validBits = descriptor.y;
    uint laneBit = 1u << gl_LocalInvocationID.x;
    if ((validBits & laneBit) == 0u) return;

    uint modelIndex = descriptor.x;
    ModelMeta meta = models[modelIndex];
    uint instIdx = (page << 5u) + gl_LocalInvocationID.x;
    NtmInstance inst = instances[instIdx];

    if (inst.InstanceColor.a <= 0.0) return;

    vec3 center = (inst.ModelMat * vec4(meta.boundingSphere.xyz, 1.0)).xyz;
    float scale = max(max(length(inst.ModelMat[0].xyz),
                           length(inst.ModelMat[1].xyz)),
                      length(inst.ModelMat[2].xyz));
    float radius = meta.boundingSphere.w * scale;

    for (int i = 0; i < 6; i++) {
        vec4 plane = FrustumPlanes[i];
        float d = dot(plane.xyz, center) + plane.w;
        if (d < -radius) return;
    }

    if (HizEnabled != 0u) {
        vec3 viewC = (ModelView * vec4(center, 1.0)).xyz;
        if (occluded(viewC, radius)) return;
    }

    uint slot = atomicAdd(models[modelIndex].instanceCount, 1u);
    if ((meta.flags & 1u) != 0u) {
        vec3 toCam = center - CameraPosRO;
        float distSq = dot(toCam, toCam);
        uint key16 = (floatBitsToUint(distSq) >> 16) ^ 0xFFFFu;
        visList[meta.visListBaseOffset + slot] = uvec2(key16 << 16, instIdx);
    } else {
        visList[meta.visListBaseOffset + slot] = uvec2(0u, instIdx);
    }
}
