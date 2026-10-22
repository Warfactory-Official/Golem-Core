layout(location = 0) in vec3 _gr_position;
layout(location = 1) in vec3 _gr_normal;
layout(location = 2) in vec2 _gr_uv;

layout(location = 3) in vec4 _gr_weights;
layout(location = 4) in vec2 _gr_joints;
layout(location = 5) in float _gr_morphSet;

layout(location = 6) in mat4 _gr_pose;
layout(location = 10) in uint _gr_boneBase;
layout(location = 11) in uint _gr_morphBase;
layout(location = 12) in vec2 _gr_light;
layout(location = 13) in vec4 _gr_color;
layout(location = 14) in vec4 _gr_overlayUv;
// (layer or -1, repeats per block, reference 0xRRGGBB, rest palette base)
layout(location = 15) in vec4 _gr_paint;

uniform mat4 _gr_modelView;
uniform mat4 _gr_projection;

out vec2 _gr_texCoord;
out vec2 _gr_lightCoord;
out vec4 _gr_tint;
out vec3 _gr_shadeNormal;
out vec2 _gr_overlayCoord;
out vec3 _gr_paintCoord;
flat out int _gr_paintLayer;
flat out int _gr_paintReference;
#ifdef GEMRENDER_LIGHTS
out vec3 _gr_lightPos;
#endif

void main() {
    vec3 position = _gr_position;
    vec3 normal = _gr_normal;

    gemrender_applyMorph(_gemrender_bones, _gr_morphBase, int(_gr_morphSet), uint(gl_VertexID),
            position, normal);

    mat4 skin = gemrender_skinMatrix(_gr_boneBase, _gr_joints, _gr_weights);

    vec4 posed = _gr_pose * (skin * vec4(position, 1.0));
    gl_Position = _gr_projection * _gr_modelView * posed;
#ifdef GEMRENDER_LIGHTS
    _gr_lightPos = posed.xyz;
#endif

    _gr_shadeNormal = normalize(mat3(_gr_pose) * (mat3(skin) * normal));

    _gr_texCoord = _gr_uv + _gr_overlayUv.zw;
    _gr_lightCoord = _gr_light;
    _gr_tint = _gr_color;
    _gr_overlayCoord = _gr_overlayUv.xy;
    _gr_paintCoord = _gr_paint.x < 0.0 ? vec3(0.0)
            : (gemrender_skinMatrix(uint(_gr_paint.w), _gr_joints, _gr_weights) * vec4(position, 1.0)).xyz * _gr_paint.y;
    _gr_paintLayer = int(_gr_paint.x);
    _gr_paintReference = int(_gr_paint.z);
}
