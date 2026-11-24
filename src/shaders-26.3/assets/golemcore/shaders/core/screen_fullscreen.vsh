#version 330
#extension GL_ARB_separate_shader_objects : require

// Fullscreen triangle for the screen-effect passes. Position.z carries the pass time (wall-clock seconds):
// 26.1 pipelines have no scalar custom-uniform binding, so every era reads it from the vertex instead.
layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 texCoord;
layout(location = 1) out float passTime;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
    passTime = Position.z;
}
