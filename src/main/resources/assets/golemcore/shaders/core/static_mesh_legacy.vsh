#version 150
#extension GL_ARB_gpu_shader5 : enable
#extension GL_ARB_gpu_shader_fp64 : enable
#ifdef GL_ARB_gpu_shader5
#define PRECISE precise
#else
#define PRECISE
#endif

// StaticMeshes (1.21.1): vanilla rendertype_entity_cutout.vsh fed from a model-space mesh. The per-vertex constants
// of an immediate entity draw come from uniforms; the pose and normal transform and the byte quantisation of the
// normal are done here, as VertexConsumer and BufferBuilder do them on the CPU.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

uniform mat4 MeshPose;
uniform mat3 MeshNormal;
uniform vec4 MeshColor;
uniform ivec2 MeshLight;
uniform ivec2 MeshOverlay;
uniform int MeshNormalize;

out float vertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;
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
    vec3 normal = golem_transform_normal(MeshNormal, Normal, MeshNormalize != 0);

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);

    vertexDistance = fog_distance(position, FogShape);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, MeshColor);
    lightMapColor = texelFetch(Sampler2, MeshLight / 16, 0);
    overlayColor = texelFetch(Sampler1, MeshOverlay, 0);
    texCoord0 = UV0;
}
