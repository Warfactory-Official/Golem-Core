package dev.golem.core.flywheel;

//? if neoforge && >=26.1 {

public final class Mesh {

    public final String debugName;
    float[] data;
    int[] indices;
    int vertexCount;
    int indexCount;
    int baseVertex;
    int firstIndex;
    float boundsX;
    float boundsY;
    float boundsZ;
    float boundsRadius;

    Mesh(String debugName, float[] data, int vertexCount, int[] indices, int indexCount) {
        this.debugName = debugName;
        replace(data, vertexCount, indices, indexCount);
    }

    void replace(float[] data, int vertexCount, int[] indices, int indexCount) {
        this.data = data;
        this.vertexCount = vertexCount;
        this.indices = indices;
        this.indexCount = indexCount;
        computeBounds();
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public int baseVertex() {
        return baseVertex;
    }

    public int firstIndex() {
        return firstIndex;
    }

    public boolean empty() {
        return data == null || indices == null || vertexCount == 0 || indexCount == 0;
    }

    public float boundsX() {
        return boundsX;
    }

    public float boundsY() {
        return boundsY;
    }

    public float boundsZ() {
        return boundsZ;
    }

    public float boundsRadius() {
        return boundsRadius;
    }

    private void computeBounds() {
        if (data == null || vertexCount == 0) {
            boundsX = boundsY = boundsZ = boundsRadius = 0.0F;
            return;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        int floats = vertexCount * 8;
        for (int off = 0; off < floats; off += 8) {
            float x = data[off];
            float y = data[off + 1];
            float z = data[off + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        boundsX = (minX + maxX) * 0.5F;
        boundsY = (minY + maxY) * 0.5F;
        boundsZ = (minZ + maxZ) * 0.5F;
        float radiusSq = 0.0F;
        for (int off = 0; off < floats; off += 8) {
            float dx = data[off] - boundsX;
            float dy = data[off + 1] - boundsY;
            float dz = data[off + 2] - boundsZ;
            float d = dx * dx + dy * dy + dz * dz;
            if (d > radiusSq) radiusSq = d;
        }
        boundsRadius = (float) Math.sqrt(radiusSq);
    }
}
//?}
