// Glow materials: location 0 -> (rgb tau, tau), location 1 -> emission (Absorbance.EMISSION_SLOT).
#ifdef _FLW_EVALUATE
layout(location = 1) out vec4 gemrender_emission;
#endif

// Overlay.x = heat x GEMRENDER_HEAT_SCALE (gemrender_particleOverlay); useOverlay off.
const float GEMRENDER_HEAT_SCALE = 1023.0;

float gemrender_fragHeat() {
    return clamp(float(flw_fragOverlay.x) / GEMRENDER_HEAT_SCALE, 0.0, 1.0);
}
