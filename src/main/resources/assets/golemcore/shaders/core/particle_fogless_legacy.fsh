#version 150

// particle_faint_legacy.fsh without fog: a 1.7 particle drawn inside glDisable(GL_FOG).

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

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
