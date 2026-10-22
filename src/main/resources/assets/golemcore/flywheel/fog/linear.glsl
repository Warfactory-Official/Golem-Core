// 26.1's environmental fog: a linear ramp over the spherical distance (fog.glsl linear_fog_value).
vec4 flw_fogFilter(vec4 color) {
    float range = flw_fogRange.y - flw_fogRange.x;
    float fog = flw_distance <= flw_fogRange.x ? 0.0
            : flw_distance >= flw_fogRange.y ? 1.0 : (flw_distance - flw_fogRange.x) / range;
    return vec4(mix(color.rgb, flw_fogColor.rgb, fog * flw_fogColor.a), color.a);
}
