uniform sampler2D _gr_depth;
uniform sampler2D _gr_lightmap;
uniform sampler3D _gr_field;
uniform mat4 _gr_clipToRelative;
uniform vec3 _gr_boxMin;
uniform vec3 _gr_boxMax;
uniform float _gr_extinction;
uniform float _gr_reach;
uniform vec3 _gr_tint;
uniform vec2 _gr_light;
// Volume mode (_gr_steps > 0): optical depth = extinction x sum(densityByShape(shape(p)) dt), shape as
// gemrender_volumeShape; colour = tint (ambient + sunT HG(dir.L, g) sunStrength), clamped as the march.
uniform int _gr_steps;
uniform float _gr_byShape[17];
uniform vec3 _gr_center;
uniform vec3 _gr_extent;
uniform float _gr_edge;
uniform vec3 _gr_fieldOrigin;
uniform float _gr_fieldScale;
uniform float _gr_ambient;
uniform float _gr_sun;
uniform float _gr_phase;
uniform vec3 _gr_lightDir;

out vec4 frag;

float byShape(float s) {
    float x = clamp(s, 0.0, 1.0) * 16.0;
    int i = min(int(x), 15);
    return mix(_gr_byShape[i], _gr_byShape[i + 1], x - float(i));
}

float shapeAt(vec3 rel) {
    vec3 unit = (rel - _gr_center) / max(_gr_extent, vec3(1e-3));
    if (_gr_fieldScale > 0.0) {
        return texture(_gr_field, _gr_fieldOrigin + clamp(unit * 0.5 + 0.5, 0.0, 1.0) * _gr_fieldScale).r;
    }
    float f = clamp((1.0 - length(unit)) / max(_gr_edge, 1e-3), 0.0, 1.0);
    return f * f * (3.0 - 2.0 * f);
}

// Camera-relative; camera inside the box: path = min(ray to scene, reach, box exit).
// Premultiplied, blend (ONE, ONE_MINUS_SRC_ALPHA): dst T + C (1 - T).
void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec2 ndc = gl_FragCoord.xy / vec2(textureSize(_gr_depth, 0)) * 2.0 - 1.0;
    vec4 h = _gr_clipToRelative * vec4(ndc, texelFetch(_gr_depth, px, 0).r * 2.0 - 1.0, 1.0);
    vec3 rel = h.xyz / h.w;
    float d = length(rel);
    vec3 dir = rel / max(d, 1e-6);
    vec3 safe = mix(vec3(1e-6), vec3(-1e-6), lessThan(dir, vec3(0.0)));
    vec3 ray = mix(dir, safe, lessThan(abs(dir), vec3(1e-6)));
    vec3 far = max(_gr_boxMin / ray, _gr_boxMax / ray);
    float exit = max(min(min(far.x, far.y), far.z), 0.0);
    float len = min(min(d, _gr_reach), exit);

    float depth = _gr_extinction * len;
    vec3 c = _gr_tint;
    if (_gr_steps > 0) {
        float dt = len / float(_gr_steps);
        float sum = 0.0;
        for (int i = 0; i < _gr_steps; i++) {
            sum += byShape(shapeAt(dir * ((float(i) + 0.5) * dt)));
        }
        depth = _gr_extinction * sum * dt;
        float gg = _gr_phase * _gr_phase;
        float k = max(1.0 + gg - 2.0 * _gr_phase * dot(dir, _gr_lightDir), 1e-4);
        c = clamp(_gr_tint * (_gr_ambient + _gr_sun * (1.0 - gg) / (k * sqrt(k))), 0.0, 1.0);
    }
    float t = exp(-depth);
    c *= texture(_gr_lightmap, clamp(_gr_light, 0.5 / 16.0, 15.5 / 16.0)).rgb;
    frag = vec4(c * (1.0 - t), 1.0 - t);
}
