#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);
    float size = gemrender_particleAlive(p, age)
            ? gemrender_particleSize(p, s, unitAge) * gemrender_bodyScale(s, unitAge) : 0.0;

    mat3 basis = gemrender_bodyBasis(p, s, age);

    flw_vertexPos = vec4(i.origin + gemrender_particlePosition(p, s, age) + basis * (flw_vertexPos.xyz * size), 1.0);
    flw_vertexNormal = basis * flw_vertexNormal;
    flw_vertexColor = vec4(gemrender_particleColor(p, s, unitAge).rgb, 1.0);
    flw_vertexOverlay = ivec2(0, 10);
    flw_vertexLight = gemrender_particleLight(p, s);
}
