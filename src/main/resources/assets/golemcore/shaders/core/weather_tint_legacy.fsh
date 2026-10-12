#version 150

// Golem Precipitation before 26.1: vanilla's particle fragment shader with 1.7's weather texture state (see
// weather_tint.fsh): the RGB is the vertex colour (times the light map), the alpha the texture's alpha times the vertex
// alpha, discarded at 0.1 or below. linear_fog is vanilla fog.glsl's with Golem's exponential-fog mark (Fogs: a start
// between -1 and 0 is GL_EXP with density -2 * start), inlined so the program needs no import.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 linear_fog(vec4 inColor, float vertexDistance, float fogStart, float fogEnd, vec4 fogColor) {
    if (fogStart < 0.0 && fogStart > -1.0) {
        float expValue = 1.0 - exp(2.0 * fogStart * vertexDistance);
        return vec4(mix(inColor.rgb, fogColor.rgb, expValue * fogColor.a), inColor.a);
    }
    if (vertexDistance <= fogStart) {
        return inColor;
    }

    float fogValue = vertexDistance < fogEnd ? smoothstep(fogStart, fogEnd, vertexDistance) : 1.0;
    return vec4(mix(inColor.rgb, fogColor.rgb, fogValue * fogColor.a), inColor.a);
}

void main() {
    vec4 color = vec4(vertexColor.rgb, texture(Sampler0, texCoord0).a * vertexColor.a) * ColorModulator;
    if (color.a <= 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
