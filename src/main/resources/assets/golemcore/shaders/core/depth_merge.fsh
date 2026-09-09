#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    float mainDepth = texelFetch(Sampler0, pixel, 0).r;
    float terrainDepth = texelFetch(Sampler1, pixel, 0).r;
    float entityDepth = texelFetch(Sampler2, pixel, 0).r;
    gl_FragDepth = min(mainDepth, min(terrainDepth, entityDepth));
    fragColor = vec4(0.0);
}
