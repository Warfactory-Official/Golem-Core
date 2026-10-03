#version 150

// Bloom's composite: level 0 (half the screen size, already blurred both ways) sampled once, bilinearly, at each
// screen pixel and added over the frame by the pass's ONE / ONE blend. The level is smooth at its own texel scale,
// so the bilinear reconstruction is piecewise linear between texels, with no steps.
uniform sampler2D Sampler0;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord);
}
