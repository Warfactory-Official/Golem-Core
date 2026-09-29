#version 150

// bloom_v.frag combined into the next-larger level with glBlendEquation(GL_MAX), which ignores the blend
// factors: result = max(destination, source). 26.1 pipelines have no blend-equation control, so every
// era takes the maximum here against a copy-free read of the destination level (Sampler1, same size as
// the output) and writes it to that level's spare target.
const float gauss[8] = float[](0.197448, 0.174697, 0.120999, 0.065602, 0.02784, 0.009246, 0.002403, 0.000489);

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

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
    fragColor = max(texelFetch(Sampler1, ivec2(gl_FragCoord.xy), 0), color);
}
