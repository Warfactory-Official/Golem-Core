#version 150

// golem_torex_flare.vsh for 1.20.1 and 1.21.1: no fog distance, so no fog. The lightmap texel is read as
// light.glsl's minecraft_sample_lightmap does on both.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;

out vec4 vertexColor;
out vec2 texCoord0;
out vec4 lightMapColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color * ColorModulator;
    texCoord0 = UV0;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
}
