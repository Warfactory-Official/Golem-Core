#version 460 core

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // Sampler0 is bound to one of ModelBakery.BREAKING_LOCATIONS (destroy_stage_0..9). The
    // CRUMBLING pipeline uses multiplicative blending (DST * SRC); discard transparent pixels
    // so they don't darken via alpha=0 contribution.
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    color *= ColorModulator;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
