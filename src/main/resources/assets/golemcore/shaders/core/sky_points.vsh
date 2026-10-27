#version 330

// Native one-pixel sky points, using the same matrix blocks and varyings as vanilla's position/color shaders.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
out vec4 vertexColor;
#ifdef TEXTURED
in vec2 UV0;
out vec2 texCoord0;
#endif

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_PointSize = 1.0;
    vertexColor = Color;
#ifdef TEXTURED
    texCoord0 = UV0;
#endif
}
