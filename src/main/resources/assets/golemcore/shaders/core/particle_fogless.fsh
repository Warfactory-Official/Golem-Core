#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// particle_faint.fsh without fog: a 1.7 particle drawn inside glDisable(GL_FOG).

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
