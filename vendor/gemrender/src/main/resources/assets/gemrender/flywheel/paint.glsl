#include "gemrender:paint_pack.glsl"

uniform sampler2DArray _gemrender_paint;

// Triplanar blend sharpness.
const float GEMRENDER_PAINT_SHARPNESS = 4.0;

// Face normal from the pattern coordinate: flat per triangle, no extra varying.
vec3 gemrender_pattern(int layer, vec3 at) {
    vec3 facing = abs(cross(dFdx(at), dFdy(at)));
    vec3 weight = pow(facing / max(1e-30, max(facing.x, max(facing.y, facing.z))),
            vec3(GEMRENDER_PAINT_SHARPNESS));
    weight /= weight.x + weight.y + weight.z;
    return texture(_gemrender_paint, vec3(at.zy, layer)).rgb * weight.x
    + texture(_gemrender_paint, vec3(at.xz, layer)).rgb * weight.y
    + texture(_gemrender_paint, vec3(at.xy, layer)).rgb * weight.z;
}

// (paint colour, coverage) from the emissive band's alpha (SurfaceBake.coverage): [0,120] solid, [136,255]
// pattern. Both sampled unconditionally: derivatives inside a per-texel branch are undefined.
vec4 gemrender_paint(int layer, vec3 at, float encoded) {
    vec3 pattern = gemrender_pattern(layer, at);
    vec3 solid = textureLod(_gemrender_paint, vec3(0.5, 0.5, float(layer)), 16.0).rgb;
    float a = encoded * 255.0;
    return a >= 128.0 ? vec4(pattern, clamp((a - 136.0) / 119.0, 0.0, 1.0)) : vec4(solid, clamp(a / 120.0, 0.0, 1.0));
}
