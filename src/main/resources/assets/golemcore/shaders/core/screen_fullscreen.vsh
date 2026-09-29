#version 150

// Fullscreen triangle for the screen-effect passes. Position.z carries the pass time (wall-clock seconds):
// 26.1 pipelines have no scalar custom-uniform binding, so every era reads it from the vertex instead.
in vec3 Position;

out vec2 texCoord;
out float passTime;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
    passTime = Position.z;
}
