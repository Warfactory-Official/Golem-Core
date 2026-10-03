#version 150

// HBM 1.12 bloom_v.frag, 15-tap vertical gaussian, with taps half a source texel apart.
// Bloom's level 0 is half the screen size; 1.12 blurred it at full size one screen pixel per tap, so half a
// texel of the half-size level is the same one-screen-pixel step and the kernel keeps its on-screen width.
const float gauss[8] = float[](0.197448, 0.174697, 0.120999, 0.065602, 0.02784, 0.009246, 0.002403, 0.000489);

uniform sampler2D Sampler0;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    float stride = 0.5 / float(textureSize(Sampler0, 0).y);
    vec4 color = vec4(0.0);
    for (int i = 0; i < 15; i++) {
        int place = i - 7;
        color += gauss[abs(place)] * texture(Sampler0, texCoord + vec2(0.0, stride * float(place)));
    }
    fragColor = color;
}
