#version 330

// 1.21.11 has no minecraft:sample_lightmap.glsl; its entity shader reads the light map with texelFetch.
vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texelFetch(lightMap, uv / 16, 0);
}
