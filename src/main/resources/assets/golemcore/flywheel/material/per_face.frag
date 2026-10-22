// 26.1's PER_FACE_LIGHTING (entity.vsh): a face seen from behind is lit with both lights negated.
void flw_materialFragment() {
    vec2 light = vec2(dot(flw_light0Direction, flw_vertexNormal), dot(flw_light1Direction, flw_vertexNormal));
    if (!gl_FrontFacing) light = -light;
    vec2 lit = max(vec2(0.0), light);
    flw_fragColor.rgb *= min(1.0, (lit.x + lit.y) * 0.6 + 0.4);
}
