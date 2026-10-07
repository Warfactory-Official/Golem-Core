#version 150

// Translucency (Golem Core): writes only alpha. The pipeline's blend decides what the sampled alpha does: nothing
// (the clear before translucent terrain), or src * (1 - dst) + dst (putting the target's own alpha back after it).
uniform sampler2D Sampler0;
out vec4 fragColor;

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, texelFetch(Sampler0, ivec2(gl_FragCoord.xy), 0).a);
}
