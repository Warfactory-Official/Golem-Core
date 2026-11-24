#version 330
#extension GL_ARB_separate_shader_objects : require

// Golem Core light-map additions: the cell's addition, blended additively onto vanilla's light map.
layout(location = 0) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}
