#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Golem Precipitation (26.1): vanilla's particle fragment shader with 1.7's weather texture state (hbm-space
// WeatherProviderCelestial.enableTextureAlphaTint): GL_COMBINE with the RGB replaced by the vertex colour (here already
// times the light map) and the alpha modulated by the texture's alpha; glAlphaFunc(GL_GREATER, 0.1).

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = vec4(vertexColor.rgb, texture(Sampler0, texCoord0).a * vertexColor.a) * ColorModulator;
    if (color.a <= 0.1) {
        discard;
    }
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
