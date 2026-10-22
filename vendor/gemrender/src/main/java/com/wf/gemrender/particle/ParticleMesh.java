package com.wf.gemrender.particle;

import com.wf.gemrender.gltf.MeshGeometry;
import com.wf.gemrender.gltf.skin.BoneAttributeCodec;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

/** A glTF primitive frozen at its rest pose, for particle instance types that place it themselves. */
final class ParticleMesh implements Mesh {
    private final float[] positions;

    private final float[] normals;

    private final float[] texCoords;

    private final int[] indices;

    private final Vector4fc boundingSphere;

    private final IndexSequence indexSequence;

    private ParticleMesh(float[] positions, float[] normals, float[] texCoords, int[] indices) {
        this.positions = positions;
        this.normals = normals;
        this.texCoords = texCoords;
        this.indices = indices;
        this.boundingSphere = sphere(positions);
        this.indexSequence = (ptr, count) -> {
            for (int i = 0; i < count; i++) {
                MemoryUtil.memPutInt(ptr + (long) i * Integer.BYTES, indices[i]);
            }
        };
    }

    /** Each vertex skinned by {@code palette}, then {@code bake}. */
    static ParticleMesh bake(MeshGeometry geometry, Matrix4f[] palette, Matrix4fc bake) {
        int count = geometry.vertexCount();
        float[] positions = new float[count * 3];
        float[] normals = new float[count * 3];
        float[] texCoords = new float[count * 2];
        int[] indices = new int[geometry.indexCount()];

        Matrix4f bakeMatrix = new Matrix4f(bake);
        Matrix4f skin = new Matrix4f();
        Matrix3f normalMatrix = new Matrix3f();
        Vector3f p = new Vector3f();
        Vector3f n = new Vector3f();

        for (int v = 0; v < count; v++) {
            int packed = geometry.packedJoints(v);
            float sum = 0.0f;
            for (int k = 0; k < BoneAttributeCodec.INFLUENCES; k++) {
                sum += weight(geometry, v, k);
            }
            if (sum <= 0.0f) {
                skin.identity();
            } else {
                skin.zero();
                for (int k = 0; k < BoneAttributeCodec.INFLUENCES; k++) {
                    float w = weight(geometry, v, k);
                    if (w > 0.0f) {
                        skin.fma4x3(palette[BoneAttributeCodec.unpackJoint(packed, k)], w / sum);
                    }
                }
                skin.m33(1.0f);
            }
            bakeMatrix.mul(skin, skin);
            skin.normal(normalMatrix);

            skin.transformPosition(p.set(geometry.position(v, 0), geometry.position(v, 1), geometry.position(v, 2)));
            normalMatrix.transform(n.set(geometry.normal(v, 0), geometry.normal(v, 1), geometry.normal(v, 2)));
            if (n.lengthSquared() > 0.0f) {
                n.normalize();
            }

            positions[v * 3] = p.x;
            positions[v * 3 + 1] = p.y;
            positions[v * 3 + 2] = p.z;
            normals[v * 3] = n.x;
            normals[v * 3 + 1] = n.y;
            normals[v * 3 + 2] = n.z;
            texCoords[v * 2] = geometry.texCoord(v, 0);
            texCoords[v * 2 + 1] = geometry.texCoord(v, 1);
        }
        for (int i = 0; i < indices.length; i++) {
            indices[i] = geometry.index(i);
        }
        return new ParticleMesh(positions, normals, texCoords, indices);
    }

    /** Channel is biased {@code (q + 0.5) / 255}; the shader's weight is {@code q / 255}. */
    private static float weight(MeshGeometry geometry, int vertex, int influence) {
        return BoneAttributeCodec.decodeWeight(BoneAttributeCodec.packNormU8(geometry.weightChannel(vertex, influence)));
    }

    float position(int vertex, int axis) {
        return positions[vertex * 3 + axis];
    }

    @Override
    public int vertexCount() {
        return positions.length / 3;
    }

    @Override
    public void write(MutableVertexList vertexList) {
        for (int i = 0; i < vertexCount(); i++) {
            vertexList.x(i, positions[i * 3]);
            vertexList.y(i, positions[i * 3 + 1]);
            vertexList.z(i, positions[i * 3 + 2]);
            vertexList.normalX(i, normals[i * 3]);
            vertexList.normalY(i, normals[i * 3 + 1]);
            vertexList.normalZ(i, normals[i * 3 + 2]);
            vertexList.u(i, texCoords[i * 2]);
            vertexList.v(i, texCoords[i * 2 + 1]);
            vertexList.r(i, 1.0f);
            vertexList.g(i, 1.0f);
            vertexList.b(i, 1.0f);
            vertexList.a(i, 1.0f);
            vertexList.overlay(i, OverlayTexture.NO_OVERLAY);
            vertexList.light(i, 0);
        }
    }

    @Override
    public IndexSequence indexSequence() {
        return indexSequence;
    }

    @Override
    public int indexCount() {
        return indices.length;
    }

    @Override
    public Vector4fc boundingSphere() {
        return boundingSphere;
    }

    private static Vector4fc sphere(float[] positions) {
        if (positions.length == 0) {
            return new Vector4f();
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        float cx = (minX + maxX) * 0.5f, cy = (minY + maxY) * 0.5f, cz = (minZ + maxZ) * 0.5f;
        float radiusSq = 0.0f;
        for (int i = 0; i < positions.length; i += 3) {
            float dx = positions[i] - cx, dy = positions[i + 1] - cy, dz = positions[i + 2] - cz;
            radiusSq = Math.max(radiusSq, dx * dx + dy * dy + dz * dz);
        }
        return new Vector4f(cx, cy, cz, (float) Math.sqrt(radiusSq));
    }
}
