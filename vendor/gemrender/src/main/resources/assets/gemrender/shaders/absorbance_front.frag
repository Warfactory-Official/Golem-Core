uniform sampler2D _gr_frontAccumulate;
uniform sampler2D _gr_frontEmission;
uniform sampler2D _gr_cloudDepth;
uniform float _gr_cloudPhase;

out vec4 frag;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    if (_gr_cloudPhase >= 0. && (texelFetch(_gr_cloudDepth, px, 0).r < 1. ? 1. : 0.) != _gr_cloudPhase) {
        discard;
    }
    frag = gemrender_absorbance(texelFetch(_gr_frontAccumulate, px, 0), texelFetch(_gr_frontEmission, px, 0).rgb);
}
