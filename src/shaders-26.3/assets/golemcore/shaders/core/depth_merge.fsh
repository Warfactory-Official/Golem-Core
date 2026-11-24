#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float mainDepth = texelFetch(Sampler0, pixel, 0).r;
    float terrainDepth = texelFetch(Sampler1, pixel, 0).r;
    float entityDepth = texelFetch(Sampler2, pixel, 0).r;
    gl_FragDepth = max(mainDepth, max(terrainDepth, entityDepth));
    fragColor = vec4(0.0);
}
