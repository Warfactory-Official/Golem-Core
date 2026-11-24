#version 330
#extension GL_ARB_separate_shader_objects : require

// HBM 1.12 bloomData pass: the texture times the vertex colour, fixed-function alpha test (> 0.1) still on,
// blended SRC_ALPHA / ONE by the render type.
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a <= 0.1) {
        discard;
    }
    fragColor = color;
}
