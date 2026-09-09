#version 460 core

#extension GL_ARB_shader_draw_parameters : require

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

// Same NtmInstance struct + 112-byte stride as the INSTANCING UBO path
// (com.hbm.render.flywheel.instance.AbstractInstancer.writeStd140); INDIRECT just sources the
// bytes from an SSBO instead of a UBO array. layout(std140) chosen for parity — both std140
// and std430 yield identical layout for this struct (all members 16-byte aligned), but std140
// keeps the CPU-side method name accurate.
struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    // UVTransform: (offsetU, offsetV, scaleU, scaleV). See golem_flywheel.vsh for context.
    vec4  UVTransform;
};

// Bound raw via glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, group.ssbo) by
// IndirectDrawManager.drawStage — Mojang's pipeline doesn't manage SSBO bindings, but they live
// in a separate GL binding namespace from UBOs so there's no conflict with built-ins (Projection,
// Lighting, DynamicTransforms) which Mojang assigns to GL_UNIFORM_BUFFER bindings 0..N.
layout(std140, binding = 0) buffer Instances {
    NtmInstance ntmInstances[];
};

// VisList is populated by golem_cull.csh and (for sortable materials) re-ordered by the 2-pass
// radix sort (golem_sort_hist/scan/scatter.csh) or the single-workgroup bitonic shader for small
// per-draw work.
// Layout: .x = depth key in sortable mode (key16 << 16; unused otherwise), .y = SSBO instance
// index. Stride is 8 B per slot (uvec2). The indirect command's baseInstance is set to the
// per-draw visListBaseOffset (in uvec2 units) rather than the SSBO offset, so
// gl_BaseInstanceARB + gl_InstanceID is the visList lookup key.
layout(std430, binding = 1) buffer VisList {
    uvec2 visList[];
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
// Mirrors golem_flywheel.vsh — render-origin-space fragment position for ShaderLights.
out vec3 renderOriginPos;
#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
// Mirrors golem_flywheel.vsh — InstanceColor.rgb hijacked as slide-offset, UVTransform hijacked
// as plane equation (nx, ny, nz, distance). See Material.CUTOUT_CLIP_SLAB / CUTOUT_CLIP_HALFSPACE.
out vec3 clipSlidPos;
flat out vec4 clipPlane;
#endif

void main() {
    uint visIdx = uint(gl_BaseInstanceARB) + uint(gl_InstanceID);
    uint instIdx = visList[visIdx].y;
    NtmInstance inst = ntmInstances[instIdx];

    // Dead-instance sentinel — same trick as golem_flywheel.vsh. Color.a = 0 means the slot is
    // freed (or explicitly invisible); cull the primitive by punching gl_Position outside clip
    // space so we don't write depth at the geometry's location and shade it black.
    if (inst.InstanceColor.a <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
    vec3 slidPos = Position + inst.InstanceColor.rgb;
    vec4 roPos = inst.ModelMat * vec4(slidPos, 1.0);
    vec4 viewPosition = ModelViewMat * roPos;
    clipSlidPos = slidPos;
    clipPlane = inst.UVTransform;
#else
    vec4 roPos = inst.ModelMat * vec4(Position, 1.0);
    vec4 viewPosition = ModelViewMat * roPos;
#endif
    renderOriginPos = roPos.xyz;
    gl_Position = ProjMat * viewPosition;

    sphericalVertexDistance = fog_spherical_distance(viewPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPosition.xyz);

#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
    vec4 baseColor = vec4(1.0, 1.0, 1.0, inst.InstanceColor.a);
#else
    vec4 baseColor = inst.InstanceColor;
#endif

#ifdef NO_LIGHTING
    // Particle materials disable directional shading — see golem_flywheel.vsh for context.
    vertexColor = baseColor;
#else
    vec3 normal = normalize(mat3(inst.ModelMat) * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, baseColor);
#endif
    lightMapColor = sample_lightmap(Sampler2, inst.LightCoords);
    overlayColor = texelFetch(Sampler1, inst.OverlayCoords, 0);
#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
    texCoord0 = UV0;
#else
    texCoord0 = UV0 * inst.UVTransform.zw + inst.UVTransform.xy;
#endif

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
