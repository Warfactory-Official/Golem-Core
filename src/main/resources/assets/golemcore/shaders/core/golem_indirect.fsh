#version 460 core

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

// Mirrors golem_flywheel.fsh ShaderLights binding — see its comments for layout/cost.
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
    if (abs(dot(clipPlane.xyz, clipSlidPos)) > clipPlane.w) {
        discard;
    }
#endif
#ifdef CLIP_HALFSPACE
    if (dot(clipPlane.xyz, clipSlidPos) > clipPlane.w) {
        discard;
    }
#endif
    vec4 color = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (color.a * vertexColor.a * ColorModulator.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    color *= vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#ifndef NO_LIGHTMAP
    color *= lightMapColor;
#endif

    vec3 emissive = vec3(0.0);
#ifndef NO_DYNAMIC_LIGHTS
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
#endif
    color.rgb += emissive * color.a;

#ifdef NO_FOG
    fragColor = color;
#else
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
#endif
}
