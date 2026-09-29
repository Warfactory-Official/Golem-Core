#version 150

// HBM 1.12 shaders/heat_distortion_post.frag. Sampler0 is the accumulated distortion amount (red),
// Sampler1 the noise texture, Sampler2 a detached copy of the frame (1.12 sampled the framebuffer it
// was writing to). time = (currentTimeMillis % 10000000) / 1000.
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    float amount = texture(Sampler0, texCoord).r;
    vec2 offset = texture(Sampler1, texCoord * 3.0 + passTime * 0.3).rg * 2.0 - 1.0;
    // 1.12 wrote pow(x, vec2(2)) with x in [-1, 1]. pow is undefined for a negative base; the constant
    // exponent 2 compiles to x * x on the drivers the effect was authored on, which is what is kept here.
    vec2 weight = texture(Sampler1, texCoord * 0.3 - passTime * 0.05).gb * 2.0 - 1.0;
    offset *= weight * weight;
    offset = offset * amount * 0.1;
    fragColor = texture(Sampler2, texCoord + offset);
}
