#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

// Golem Precipitation: vanilla's particle fragment shader with 1.7's weather texture state (hbm-space
// WeatherProviderCelestial.enableTextureAlphaTint): GL_COMBINE with the RGB replaced by the vertex colour (here already
// times the light map) and the alpha modulated by the texture's alpha; glAlphaFunc(GL_GREATER, 0.1).
// The OIT_* defines are vanilla's order-independent transparency stages, handled as particle.fsh handles them.

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec4 color = vec4(vertexColor.rgb, texture(Sampler0, texCoord0).a * vertexColor.a) * ColorModulator;
    if (color.a <= 0.1) {
        discard;
    }
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
    #ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
    vec4 fogColor = vec4(FogColor.rgb * color.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
    #endif
}
