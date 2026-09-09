#version 460 core

// Minimal fragment shader for the Fabulous-mode depth-only pass. Color writes are masked off
// at the pipeline level (ColorTargetState.WRITE_NONE), so this shader only needs to sample
// Sampler0 for the alpha-discard test that prevents writing depth at fully-transparent quad
// corners — without it, transparent quad bounds would write phantom particle depth and the
// post-chain (transparency.fsh) would sort our layer at those phantom depths.
//
// Only gl_FragDepth is the meaningful output (written by the rasterizer from gl_Position).
// fragColor is declared because GL requires a color output for fragment shaders, but its
// write is masked off by ColorTargetState.WRITE_NONE.

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (texture(Sampler0, texCoord0).a < ALPHA_CUTOUT) discard;
    fragColor = vec4(0.0);
}
