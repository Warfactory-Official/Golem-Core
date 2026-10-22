// The mesh's own sphere is meaningless - its vertices are curve parameters, not positions - so the
// instance carries the real one, exactly as the skinned path does. Leaving it at its default culls
// the rope away at the edges of the screen, on the indirect backend only, with nothing said.

void flw_transformBoundingSphere(in FlwInstance i, inout vec3 center, inout float radius) {
    center = i.sphere.xyz;
    radius = i.sphere.w;
}
