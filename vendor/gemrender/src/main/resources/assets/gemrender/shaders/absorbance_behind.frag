uniform sampler2D _gr_accumulate;
uniform sampler2D _gr_frontAccumulate;
uniform sampler2D _gr_emission;
uniform sampler2D _gr_frontEmission;

out vec4 frag;

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec4 behind = max(texelFetch(_gr_accumulate, px, 0) - texelFetch(_gr_frontAccumulate, px, 0), vec4(0.));
    vec3 glow = max(texelFetch(_gr_emission, px, 0).rgb - texelFetch(_gr_frontEmission, px, 0).rgb, vec3(0.));
    frag = gemrender_absorbance(behind, glow);
}
