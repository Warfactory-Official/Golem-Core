#version 330
#extension GL_ARB_separate_shader_objects : require

// HBM 1.12 shaders/heat_distortion_new.frag: amount * smoothstep(0, 1, mask alpha) into red, blended ONE / ONE.
// The amount (1.12's "amount" uniform) is the vertex colour's red channel; 26.1 has no scalar uniform binding.
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 mask = texture(Sampler0, texCoord0);
    fragColor = vec4(vertexColor.r * smoothstep(0.0, 1.0, mask.a), 0.0, 0.0, 1.0);
}
