#version 330
#extension GL_ARB_separate_shader_objects : require

// Native one-pixel sky points, using the same matrix blocks and varyings as vanilla's position/color shaders.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
// Linked with vanilla position_color (vertexColor at 0) or, TEXTURED, position_tex_color (texCoord0 at 0).
#ifdef TEXTURED
layout(location = 2) in vec2 UV0;
layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
#else
layout(location = 0) out vec4 vertexColor;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_PointSize = 1.0;
    vertexColor = Color;
#ifdef TEXTURED
    texCoord0 = UV0;
#endif
}
