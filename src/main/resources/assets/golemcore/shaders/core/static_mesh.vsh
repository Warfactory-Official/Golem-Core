#version 330
#extension GL_ARB_gpu_shader5 : enable
#extension GL_ARB_gpu_shader_fp64 : enable
#ifdef GL_ARB_gpu_shader5
#define PRECISE precise
#else
#define PRECISE
#endif

// StaticMeshes (26.1): vanilla core/entity.vsh, fed from a mesh uploaded once in model space. The per-vertex
// constants of an immediate entity draw (colour, overlay, light) come from the StaticMesh block, and the CPU work
// of VertexConsumer.addVertex(pose, ...), setNormal(pose, ...) and BufferBuilder.setNormal is done here. Everything
// after the inputs is entity.vsh unchanged; the fragment stage is vanilla core/entity itself.

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

layout(std140) uniform StaticMesh {
    mat4 MeshPose;
    mat4 MeshNormal;
    vec4 MeshColor;
    ivec4 MeshCoords;
    ivec4 MeshFlags;
};

#ifndef NO_OVERLAY
uniform sampler2D Sampler1;
#endif

#ifndef EMISSIVE
uniform sampler2D Sampler2;
#endif

out float sphericalVertexDistance;
out float cylindricalVertexDistance;

#ifdef PER_FACE_LIGHTING
out vec4 vertexPerFaceColorBack;
out vec4 vertexPerFaceColorFront;
#else
out vec4 vertexColor;
#endif

#ifndef EMISSIVE
out vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
out vec4 overlayColor;
#endif

out vec2 texCoord0;

// The CPU arithmetic, operation for operation (JOML without FMA: every product and sum rounded on its own). precise
// keeps the compiler from fusing a*b+c into one rounding or reordering, so the same floats come out.
vec3 golem_transform_position(mat4 m, vec3 p) {
    PRECISE float x = m[0][0] * p.x + (m[1][0] * p.y + (m[2][0] * p.z + m[3][0]));
    PRECISE float y = m[0][1] * p.x + (m[1][1] * p.y + (m[2][1] * p.z + m[3][1]));
    PRECISE float z = m[0][2] * p.x + (m[1][2] * p.y + (m[2][2] * p.z + m[3][2]));
    return vec3(x, y, z);
}

vec3 golem_transform_normal(mat3 m, vec3 n, bool normalise) {
    PRECISE float x = m[0][0] * n.x + (m[1][0] * n.y + m[2][0] * n.z);
    PRECISE float y = m[0][1] * n.x + (m[1][1] * n.y + m[2][1] * n.z);
    PRECISE float z = m[0][2] * n.x + (m[1][2] * n.y + m[2][2] * n.z);
    if (normalise) {
        // Vector3f.normalize: 1.0f / (float) Math.sqrt(x * x + (y * y + z * z)), the root and the quotient correctly
        // rounded, which a float sqrt and divide on a GPU are not; doubles round them right (when the GPU has them)
        PRECISE float sum = x * x + (y * y + z * z);
#ifdef GL_ARB_gpu_shader_fp64
        PRECISE float root = float(sqrt(double(sum)));
        PRECISE float scalar = float(1.0LF / double(root));
#else
        PRECISE float root = sqrt(sum);
        PRECISE float scalar = 1.0 / root;
#endif
        x = x * scalar;
        y = y * scalar;
        z = z * scalar;
    }
    PRECISE vec3 scaled = clamp(vec3(x, y, z), -1.0, 1.0) * 127.0;
    return trunc(scaled) / 127.0;
}

void main() {
    vec3 position = golem_transform_position(MeshPose, Position);
    vec3 normal = golem_transform_normal(mat3(MeshNormal), Normal, MeshFlags.x != 0);
    vec4 color = MeshColor;
    ivec2 uv1 = MeshCoords.zw;
    ivec2 uv2 = MeshCoords.xy;

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(position);
    cylindricalVertexDistance = fog_cylindrical_distance(position);

#ifdef PER_FACE_LIGHTING
    vec2 light = minecraft_compute_light(Light0_Direction, Light1_Direction, normal);
    vertexPerFaceColorBack = minecraft_mix_light_separate(-light, color);
    vertexPerFaceColorFront = minecraft_mix_light_separate(light, color);
#elif defined(NO_CARDINAL_LIGHTING)
    vertexColor = color;
#else
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color);
#endif

#ifndef EMISSIVE
    lightMapColor = sample_lightmap(Sampler2, uv2);
#endif

#ifndef NO_OVERLAY
    overlayColor = texelFetch(Sampler1, uv1, 0);
#endif

    texCoord0 = UV0;

#ifdef APPLY_TEXTURE_MATRIX
    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
