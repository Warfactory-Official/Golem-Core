#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Sampler0 is bound to one of ModelBakery.BREAKING_LOCATIONS (destroy_stage_0..9). Each is a
    // 16x16 atlas with transparent background and opaque dark crack pixels. The CRUMBLING pipeline
    // uses multiplicative blending (DST * SRC), so where the texture is transparent we discard
    // (no overlay) and where it's dark crack pixels we darken the framebuffer.
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
