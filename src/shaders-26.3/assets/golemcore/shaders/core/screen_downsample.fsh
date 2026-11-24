#version 330
#extension GL_ARB_separate_shader_objects : require

// HBM 1.12 shaders/downsample.frag: four bilinear taps one source texel away on each diagonal.
// The texel size was a uniform of 1/source texture size; textureSize gives the same value.
uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord;
layout(location = 1) in float passTime;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec4 color = texture(Sampler0, texCoord + vec2(texel.x, texel.y));
    color += texture(Sampler0, texCoord + vec2(-texel.x, texel.y));
    color += texture(Sampler0, texCoord + vec2(texel.x, -texel.y));
    color += texture(Sampler0, texCoord + vec2(-texel.x, -texel.y));
    fragColor = color * 0.25;
}
