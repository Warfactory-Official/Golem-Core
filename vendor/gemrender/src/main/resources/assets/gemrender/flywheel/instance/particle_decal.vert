#include "gemrender:particle.glsl"

void flw_instanceVertex(in FlwInstance i) {
    GemRenderParticle p = gemrender_particle(i.particle);
    GemRenderStyle s = gemrender_style(p.style);

    float age = flw_renderSeconds - p.spawnTime;
    float unitAge = gemrender_particleUnitAge(p, age);
    float size = gemrender_particleAlive(p, age) ? gemrender_particleSize(p, s, unitAge) : 0.0;

    flw_vertexPos = vec4(i.origin + gemrender_decalCorner(p, flw_vertexPos.xy, size), 1.0);
    flw_vertexNormal = gemrender_decalNormal(p);
    flw_vertexColor = gemrender_particleColor(p, s, unitAge);
    flw_vertexOverlay = ivec2(0, 10);
    flw_vertexLight = gemrender_particleLight(p, s);
}
