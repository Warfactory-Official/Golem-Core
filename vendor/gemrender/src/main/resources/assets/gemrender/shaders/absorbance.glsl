// Premultiplied, blend (ONE, ONE_MINUS_SRC_ALPHA): dst' = dst T + S (1 - T) + E T, T = exp(-tau).
// E T: emission sits behind the absorbing media at its pixel (ex-ADDITIVE look under OIT smoke).
vec4 gemrender_absorbance(vec4 acc, vec3 glow) {
    if (acc.a < 1e-5 && max(glow.r, max(glow.g, glow.b)) < 1e-5) {
        discard;
    }
    float t = exp(-acc.a);
    vec3 scattered = acc.a < 1e-5 ? vec3(0.) : acc.rgb / acc.a * (1. - t);
    return vec4(scattered + glow * t, 1. - t);
}
