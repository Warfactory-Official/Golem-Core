uniform sampler2D _gr_depthRange;
uniform sampler2D _gr_waterDepth;
uniform float _gr_znear;
uniform float _gr_zfar;

// Unmarked pixels (split depth 0): nearest OIT depth, as the stock composite writes. Marked: none (would
// erase the water drawn later).
void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    float nearest = -texelFetch(_gr_depthRange, px, 0).r;
    if (texelFetch(_gr_waterDepth, px, 0).r > 0. || nearest >= _gr_zfar) {
        discard;
    }
    gl_FragDepth = delinearize_depth(nearest, _gr_znear, _gr_zfar);
}
