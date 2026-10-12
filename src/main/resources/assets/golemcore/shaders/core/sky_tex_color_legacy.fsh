#version 150

// Vanilla 1.20.1/1.21.1 position_tex_color.fsh with the discard moved from alpha 0.1 to alpha 0, as 26.1's
// core/position_tex_color has it: 1.7 drew the sky with GL_ALPHA_TEST disabled, so a faint glow (a star's halo,
// a flare at 0.25) must not be cut off. Used by Golem Core's Sky for textured quads, lines and points before 26.1.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
