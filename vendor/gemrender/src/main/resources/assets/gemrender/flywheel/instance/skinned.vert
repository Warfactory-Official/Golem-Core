#include "gemrender:skin_lbs.glsl"
#include "gemrender:morph.glsl"
#include "gemrender:paint_pack.glsl"

void flw_instanceVertex(in FlwInstance i) {

    int morphSet = flw_vertexOverlay.x;

    vec3 position = flw_vertexPos.xyz;
    vec3 normal = flw_vertexNormal;

    gemrender_applyMorph(_gemrender_bones, i.morphBase, morphSet, flw_vertexId, position, normal);

    mat4 skin = gemrender_skinMatrix(i.boneBase, flw_vertexLight, flw_vertexColor);
    vec3 rest = i.paint == 0xFFFFFFFFu ? vec3(0.0)
            : (gemrender_skinMatrix(i.paintRestBase, flw_vertexLight, flw_vertexColor) * vec4(position, 1.0)).xyz;

    flw_vertexPos = i.pose * (skin * vec4(position, 1.0));

    flw_vertexNormal = mat3(i.pose) * (mat3(skin) * normal);

    // Joints/weights live in flw_vertexLight/Color: read before those are overwritten.
    if (i.jointUvBase == 0xFFFFFFFFu) {
        flw_vertexTexCoord += i.uvOffset;
    } else {
        int joints[GEMRENDER_INFLUENCES];
        float weights[GEMRENDER_INFLUENCES];
        gemrender_unpackJoints(flw_vertexLight, joints);
        gemrender_unpackWeights(flw_vertexColor, weights);
        int dominant = 0;
        for (int k = 1; k < GEMRENDER_INFLUENCES; k++) {
            if (weights[k] > weights[dominant]) {
                dominant = k;
            }
        }
        int at = int(i.jointUvBase) + joints[dominant] * 2;
        flw_vertexTexCoord += vec2(texelFetch(_gemrender_bones, at).r, texelFetch(_gemrender_bones, at + 1).r);
    }

    if (i.paint == 0xFFFFFFFFu) {
        flw_vertexColor = i.color;
        flw_vertexOverlay = ivec2(0, 10);
    } else {
        // Painted: color = pattern coordinate, overlay = (marker, reference); material/pbr.frag undoes both.
        flw_vertexColor = vec4(rest * i.paintScale, 1.0);
        flw_vertexOverlay = ivec2(-1 - int(i.paint), gemrender_packPaint(i.paintReference, i.color.a));
    }

    flw_vertexLight = vec2(i.light) / 256.0;
}
