uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_emission;
uniform sampler2D _gr_depthRange;
uniform float _gr_znear;
uniform float _gr_zfar;

out vec4 frag;

// Depth: nearest absorbance fragment (Fabulous sorts layers by it; depth mask off otherwise).
void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    frag = gemrender_absorbance(texelFetch(_gr_accumulate, px, 0), texelFetch(_gr_emission, px, 0).rgb);
    float nearest = -texelFetch(_gr_depthRange, px, 0).r;
    gl_FragDepth = delinearize_depth(nearest, _gr_znear, _gr_zfar);
}
