#version 430 core

layout(local_size_x = 64) in;

const uint SRC_OFFSET_MASK = 0xFFFFFFu;

// Lower 24 bits: source offset in uints (24 bits = 16 MB staging ring).
// Upper 8 bits: size of the copy in uints (max 64 — sized to local_size_x).
// Large transfers are split CPU-side into multiple Copy records by ScatterList.
struct Copy {
    uint sizeAndSrcOffset;
    uint dstOffset;
};

layout(std430, binding = 0) restrict readonly buffer Copies {
    Copy copies[];
};

layout(std430, binding = 1) restrict readonly buffer Src {
    uint src[];
};

layout(std430, binding = 2) restrict writeonly buffer Dst {
    uint dst[];
};

void main() {
    // One workgroup per Copy; dispatched as (copyCount, 1, 1).
    uint copy = gl_WorkGroupID.x;
    uint i = gl_LocalInvocationID.x;

    uint sizeAndSrcOffset = copies[copy].sizeAndSrcOffset;
    uint dstOffset = copies[copy].dstOffset;
    uint srcOffset = sizeAndSrcOffset & SRC_OFFSET_MASK;
    uint size = sizeAndSrcOffset >> 24;

    // Fetch before the bounds check to give the scheduler latency hiding (upstream Flywheel
    // notes a ~1ms/frame win from this on 20 MB through a 24 MB ring).
    uint toCopy = src[srcOffset + i];

    if (i >= size) {
        return;
    }

    dst[dstOffset + i] = toCopy;
}
