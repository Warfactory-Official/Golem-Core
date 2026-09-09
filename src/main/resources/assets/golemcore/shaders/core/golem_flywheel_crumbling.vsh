#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// INSTANCE_CAP injected via RenderPipeline.Builder.withShaderDefine in com.hbm.render.flywheel.Pipelines.

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

// Same per-instance struct as golem_flywheel.vsh — Engine packs the same std140 layout into the
// per-frame crumbling UBO so geometry lines up exactly with the opaque pass.
struct NtmInstance {
    ivec2 OverlayCoords;
    ivec2 LightCoords;
    vec4  InstanceColor;
    mat4  ModelMat;
    // Unused by the crumbling pass but must match AbstractInstancer.STD140_BYTES (112).
    vec4  UVTransform;
};

layout(std140) uniform Instances {
    NtmInstance ntmInstances[INSTANCE_CAP];
};

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec2 texCoord0;

void main() {
    NtmInstance inst = ntmInstances[gl_InstanceID];
    if (inst.InstanceColor.a <= 0.0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }

    vec4 viewPosition = ModelViewMat * inst.ModelMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;

    sphericalVertexDistance = fog_spherical_distance(viewPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(viewPosition.xyz);
    texCoord0 = UV0;
}
