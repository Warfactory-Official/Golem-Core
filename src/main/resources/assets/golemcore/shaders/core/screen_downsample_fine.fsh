#version 150

// screen_downsample with half the tap offset: four bilinear taps half a source texel away on each diagonal,
// i.e. the 2x2 average under each output texel. Bloom uses it for the half-size level 0 -> level 1 step, so
// level 1 keeps (to within a fraction of a pixel) the 4x4 screen-pixel footprint the full-size chain gave it.
uniform sampler2D Sampler0;

in vec2 texCoord;
in float passTime;

out vec4 fragColor;

void main() {
    vec2 texel = 0.5 / vec2(textureSize(Sampler0, 0));
    vec4 color = texture(Sampler0, texCoord + vec2(texel.x, texel.y));
    color += texture(Sampler0, texCoord + vec2(-texel.x, texel.y));
    color += texture(Sampler0, texCoord + vec2(texel.x, -texel.y));
    color += texture(Sampler0, texCoord + vec2(-texel.x, -texel.y));
    fragColor = color * 0.25;
}
