#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 renderOriginPos;
#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
in vec3 clipSlidPos;
flat in vec4 clipPlane;
#endif

// Per-VisualizationManager light buffer. Rebuilt each frame in render-origin space (matches
// renderOriginPos input), capped at 16 lights. LightCount=0 in scenes with no contributors —
// the loop is bounded by it so the cost collapses to one branch.
struct ShaderLightUBO {
    vec4 posRadius;
    vec4 color;
};

layout(std140) uniform ShaderLights {
    int LightCount;
    ShaderLightUBO Lights[16];
};

out vec4 fragColor;

void main() {
#ifdef CLIP_SLAB
    // Two-sided slab in OBJ-local space; clipPlane = (nx, ny, nz, halfExtent). Discards anywhere
    // |dot(n, slidPos)| > halfExtent. Tested before texture sampling so we skip the fetch on
    // clipped frags. Replaces 1.7's glClipPlane for slab-clipped variants.
    if (abs(dot(clipPlane.xyz, clipSlidPos)) > clipPlane.w) {
        discard;
    }
#endif
#ifdef CLIP_HALFSPACE
    // Single half-space in OBJ-local space; clipPlane = (nx, ny, nz, distance). Discards anywhere
    // dot(n, slidPos) > distance. For one-sided caps (sliding seal back, qe_containment top).
    if (dot(clipPlane.xyz, clipSlidPos) > clipPlane.w) {
        discard;
    }
#endif
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    // Quadratic falloff: att = (1 - dist/radius)^2 for dist<radius, 0 otherwise. Multiplied by
    // color.a so transparent fragments don't punch additive light through what should be glass /
    // smoke; opaque surfaces (alpha=1) get full contribution. Applied before fog so distant lit
    // fragments fog out naturally.
    vec3 emissive = vec3(0.0);
    for (int i = 0; i < LightCount; i++) {
        vec3 d = Lights[i].posRadius.xyz - renderOriginPos;
        float distSq = dot(d, d);
        float radius = Lights[i].posRadius.w;
        float r2 = radius * radius;
        if (distSq < r2) {
            float att = 1.0 - sqrt(distSq) / radius;
            emissive += Lights[i].color.rgb * (att * att);
        }
    }
    color.rgb += emissive * color.a;

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
