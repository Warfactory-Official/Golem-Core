// One fragment = one ray of a shadowed light's tile: first solid voxel entry distance, else range + 1.
// Grid exit counts as a hit: nothing beyond the window is lit.

uniform usampler3D _grs_occupancy;
uniform int _grs_tileLight[GEMRENDER_LIGHT_SHADOW_MAX];

out vec4 fragColor;

bool _grs_solid(ivec3 v, ivec3 wrap) {
    ivec3 c = (v + wrap) & (GEMRENDER_LIGHT_OCCUPANCY - 1);
    uint row = texelFetch(_grs_occupancy, ivec3(c.x >> 4, c.y, c.z), 0).r;
    return ((row >> uint(c.x & 15)) & 1u) != 0u;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    ivec2 tileXY = pixel / GEMRENDER_LIGHT_SHADOW_RES;
    int i = _grs_tileLight[tileXY.y * GEMRENDER_LIGHT_SHADOW_TILES + tileXY.x];
    if (i < 0) {
        fragColor = vec4(0.0);
        return;
    }
    vec2 uv = (vec2(pixel - tileXY * GEMRENDER_LIGHT_SHADOW_RES) + 0.5) / float(GEMRENDER_LIGHT_SHADOW_RES);
    vec4 a = _grl_lights[i * 5];
    vec4 c = _grl_lights[i * 5 + 2];
    vec4 d = _grl_lights[i * 5 + 3];

    vec3 dir;
    if (c.w > -1.5) {
        vec2 s = (uv * 2.0 - 1.0) * (sqrt(1.0 - c.w * c.w) / c.w);
        dir = normalize(c.xyz + s.x * cross(c.xyz, d.xyz) + s.y * d.xyz);
    } else {
        dir = gemrender_octDecode(uv);
    }

    // Block space relative to the camera section's origin; window = [-edge, edge).
    vec3 o = _grl_cameraFrac.xyz + a.xyz;
    ivec3 wrap = (_grl_cameraSection.xyz & (GEMRENDER_LIGHT_OCCUPANCY / 16 - 1)) * 16;
    int edge = GEMRENDER_LIGHT_OCCUPANCY / 2;
    float range = a.w;

    ivec3 v = ivec3(floor(o));
    ivec3 stepv = ivec3(dir.x >= 0.0 ? 1 : -1, dir.y >= 0.0 ? 1 : -1, dir.z >= 0.0 ? 1 : -1);
    vec3 delta = 1.0 / max(abs(dir), vec3(1.0e-8));
    vec3 next = vec3(dir.x >= 0.0 ? float(v.x + 1) - o.x : o.x - float(v.x),
            dir.y >= 0.0 ? float(v.y + 1) - o.y : o.y - float(v.y),
            dir.z >= 0.0 ? float(v.z + 1) - o.z : o.z - float(v.z)) * delta;

    // Origin voxel skipped: a light inside a block still lights.
    float t = range + 1.0;
    int steps = int(range * 1.75) + 3;
    for (int k = 0; k < steps; k++) {
        float enter;
        if (next.x < next.y && next.x < next.z) {
            enter = next.x;
            next.x += delta.x;
            v.x += stepv.x;
        } else if (next.y < next.z) {
            enter = next.y;
            next.y += delta.y;
            v.y += stepv.y;
        } else {
            enter = next.z;
            next.z += delta.z;
            v.z += stepv.z;
        }
        if (enter >= range) {
            break;
        }
        if (any(lessThan(v, ivec3(-edge))) || any(greaterThanEqual(v, ivec3(edge))) || _grs_solid(v, wrap)) {
            t = enter;
            break;
        }
    }
    fragColor = vec4(t, 0.0, 0.0, 1.0);
}
