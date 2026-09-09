#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

// INSTANCE_CAP injected via RenderPipeline.Builder.withShaderDefine in com.hbm.render.flywheel.Pipelines.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    // UVTransform: (offsetU, offsetV, scaleU, scaleV). Identity (0,0,1,1) = no UV remap;
    // sprite-sheet animations write (col/cols, row/rows, 1/cols, 1/rows) per frame.
    vec4  UVTransform;
};

layout(std140) uniform Instances {
    NtmInstance ntmInstances[INSTANCE_CAP];
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
out vec2 texCoord0;
// Render-origin-space fragment position (inst.ModelMat * Position, before ModelViewMat). The
// fragment shader walks ShaderLights (positions are also stored in render-origin space, packed
// by ShaderLightBuffer.update with the same renderOrigin) and adds quadratic-falloff additive
// contribution per light. Same vec3 in clip-mode — uses slidPos so light tests follow the
// slid geometry rather than the OBJ-local origin.
out vec3 renderOriginPos;
#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
// Clip-mode (CUTOUT_CLIP_SLAB / CUTOUT_CLIP_HALFSPACE) hijacks two per-instance slots:
//   • InstanceColor.rgb — slide-offset applied to OBJ-local Position before MV.
//   • UVTransform       — plane equation (nx, ny, nz, distance). Fragment shader tests
//                         |dot(n, slidPos)| > d (slab) or dot(n, slidPos) > d (halfspace).
// Replaces 1.7's GL clip planes (no clip-distance hook in 26.1's pipeline). Sprite-strip UV
// animation is unavailable in clip mode — texCoord0 = UV0 verbatim.
out vec3 clipSlidPos;
flat out vec4 clipPlane;
#endif

void main() {
    NtmInstance inst = ntmInstances[gl_InstanceID];

    // Dead-instance sentinel: Instancer.flush writes color=0 for slots whose visible bit is
    // false (freed slots and explicitly setVisible(false) instances). Output positions outside
    // clip space so the primitive is culled before fragment shading; otherwise we'd write
    // depth at the geometry's location and shade it black, leaving a silhouette.
    // Clip mode: InstanceColor.rgb is the slide-offset, so RGB can legitimately be zero on a
    // live instance. Only the alpha channel sentinels visibility — RGB-only zero is fine.
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
    // RGB is hijacked for slide; force white tint and preserve alpha for the sentinel guard
    // above (which has already passed by the time we reach lighting).
    vec4 baseColor = vec4(1.0, 1.0, 1.0, inst.InstanceColor.a);
#else
    vec4 baseColor = inst.InstanceColor;
#endif

#ifdef NO_LIGHTING
    // Particle materials disable directional shading — vertexColor is the raw per-instance
    // color so the artist-picked tint isn't dimmed to ambient + dot-of-light. Mirrors upstream
    // RenderType.disableLighting=true on the instanced rad fog / RBMK steam / contrail paths.
    vertexColor = baseColor;
#else
    vec3 normal = normalize(mat3(inst.ModelMat) * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, baseColor);
#endif
    lightMapColor = sample_lightmap(Sampler2, inst.LightCoords);
    overlayColor = texelFetch(Sampler1, inst.OverlayCoords, 0);
#if defined(CLIP_SLAB) || defined(CLIP_HALFSPACE)
    // UVTransform is hijacked as the clip plane equation in clip mode — UV remap is unavailable
    // for clipped variants. Caller must use untransformed atlas UVs.
    texCoord0 = UV0;
#else
    texCoord0 = UV0 * inst.UVTransform.zw + inst.UVTransform.xy;
#endif

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
