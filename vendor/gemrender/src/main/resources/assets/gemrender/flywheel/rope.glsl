// The shape of a rope, shared by instance/rope.vert and instance/cull/rope.glsl so the drawn
// geometry and the sphere it is culled against cannot disagree. Mirrored in Java by RopeCurve.

const float GEMRENDER_TAU = 6.28318530718;
const float GEMRENDER_PI = 3.14159265359;

struct GemRenderRope {
    vec3 a;
    vec3 b;
    float sag;
    float radius;
    float uvScale;
    float twist;
    vec4 sway;// amplitude, frequency (rad/s), phase, waves along the rope
};

vec3 gemrender_ropePoint(in GemRenderRope r, float t) {
    return mix(r.a, r.b, t) - vec3(0.0, r.sag * 4.0 * t * (1.0 - t), 0.0);
}


vec3 gemrender_ropeTangent(in GemRenderRope r, float t) {
    return (r.b - r.a) - vec3(0.0, r.sag * 4.0 * (1.0 - 2.0 * t), 0.0);
}


void gemrender_ropeFrame(vec3 tangent, out vec3 forward, out vec3 n1, out vec3 n2) {
    float len = length(tangent);
    forward = len > 1e-6 ? tangent / len : vec3(0.0, 1.0, 0.0);

    vec3 reference = abs(forward.y) > 0.999 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);

    n1 = normalize(cross(forward, reference));
    n2 = cross(n1, forward);
}


vec3 gemrender_ropeSway(in GemRenderRope r, float t, float time, vec3 n1, vec3 n2) {
    if (r.sway.x <= 0.0) {
        return vec3(0.0);
    }

    float phase = r.sway.z + time * r.sway.y + t * r.sway.w * GEMRENDER_TAU;
    float envelope = sin(GEMRENDER_PI * t);

    return (n1 * sin(phase) + n2 * cos(phase * 0.7)) * (r.sway.x * envelope);
}
