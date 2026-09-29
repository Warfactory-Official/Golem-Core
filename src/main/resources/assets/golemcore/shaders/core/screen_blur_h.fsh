#version 150

// HBM 1.12 shaders/bloom_h.frag: 15-tap horizontal gaussian. frag_width was 1/source width.
const float gauss[8] = float[](0.197448, 0.174697, 0.120999, 0.065602, 0.02784, 0.009246, 0.002403, 0.000489);

uniform sampler2D Sampler0;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    float fragWidth = 1.0 / float(textureSize(Sampler0, 0).x);
    vec4 color = vec4(0.0);
    for (int i = 0; i < 15; i++) {
        int place = i - 7;
        color += gauss[abs(place)] * texture(Sampler0, texCoord + vec2(fragWidth * float(place), 0.0));
    }
    fragColor = color;
}
