#version 150

// HBM 1.12 shaders/bloom_v.frag: 15-tap vertical gaussian. frag_height was 1/source height.
const float gauss[8] = float[](0.197448, 0.174697, 0.120999, 0.065602, 0.02784, 0.009246, 0.002403, 0.000489);

uniform sampler2D Sampler0;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    float fragHeight = 1.0 / float(textureSize(Sampler0, 0).y);
    vec4 color = vec4(0.0);
    for (int i = 0; i < 15; i++) {
        int place = i - 7;
        color += gauss[abs(place)] * texture(Sampler0, texCoord + vec2(0.0, fragHeight * float(place)));
    }
    fragColor = color;
}
