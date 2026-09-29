#version 330

// HBM 1.12 bloomData pass: the texture times the vertex colour, fixed-function alpha test (> 0.1) still on,
// blended SRC_ALPHA / ONE by the render type.
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a <= 0.1) {
        discard;
    }
    fragColor = color;
}
