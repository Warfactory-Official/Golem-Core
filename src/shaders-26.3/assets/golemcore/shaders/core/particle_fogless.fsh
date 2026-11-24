#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>

// particle_faint.fsh without fog: a 1.7 particle drawn inside glDisable(GL_FOG).

uniform sampler2D Sampler0;

layout(location = 2) in vec2 texCoord0;
layout(location = 3) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
