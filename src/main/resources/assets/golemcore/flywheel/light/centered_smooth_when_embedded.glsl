void flw_shaderLight() {
    #ifdef FLW_EMBEDDED
    FlwLightAo light;
    if (flw_light(flw_vertexPos.xyz, flw_vertexNormal, light)) {
        flw_fragLight = max(flw_fragLight, light.light);
        if (flw_material.ambientOcclusion) {
            flw_fragColor.rgb *= light.ao;
        }
    }
    #endif
    // Center the final lightmap sample after combining instance and embedded light.
    flw_fragLight += vec2(0.5 / 16.0);
}
