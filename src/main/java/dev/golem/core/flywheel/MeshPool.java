package dev.golem.core.flywheel;

//? if >=26.1 {

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.lwjgl.system.MemoryUtil;

public final class MeshPool {

    private static Mesh[] meshes = new Mesh[256];
    private static int meshCount;
    private static GpuBuffer vertexBuffer;
    private static GpuBuffer indexBuffer;
    private static boolean dirty = true;

    private MeshPool() {
    }

    public static synchronized int register(String debugName, float[] data, int vertexCount, int[] indices, int indexCount) {
        ensureMeshCapacity(meshCount + 1);
        int handle = meshCount++;
        meshes[handle] = new Mesh(debugName, data, vertexCount, indices, indexCount);
        dirty = true;
        return handle;
    }

    public static synchronized void replace(int handle, float[] data, int vertexCount, int[] indices, int indexCount) {
        mesh(handle).replace(data, vertexCount, indices, indexCount);
        dirty = true;
    }

    public static synchronized Mesh mesh(int handle) {
        if (handle < 0 || handle >= meshCount) {
            throw new IllegalArgumentException("Unknown mesh handle " + handle);
        }
        return meshes[handle];
    }

    public static synchronized GpuBuffer vertexBuffer() {
        ensureUploaded();
        return vertexBuffer;
    }

    public static synchronized GpuBuffer indexBuffer() {
        ensureUploaded();
        return indexBuffer;
    }

    public static synchronized void ensureUploaded() {
        if (!dirty) return;
        uploadAll();
    }

    public static synchronized void releaseGpuResources() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (indexBuffer != null) {
            indexBuffer.close();
            indexBuffer = null;
        }
        dirty = true;
    }

    private static void uploadAll() {
        RenderSystem.assertOnRenderThread();
        int totalVertices = 0;
        int totalIndices = 0;
        for (int i = 0; i < meshCount; i++) {
            Mesh mesh = meshes[i];
            mesh.baseVertex = totalVertices;
            mesh.firstIndex = totalIndices;
            totalVertices += mesh.vertexCount();
            totalIndices += mesh.indexCount();
        }

        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
        if (indexBuffer != null) {
            indexBuffer.close();
            indexBuffer = null;
        }

        if (totalVertices == 0 || totalIndices == 0) {
            dirty = false;
            return;
        }

        int vbSize = totalVertices * Layout.STRIDE;
        ByteBuffer vb = MemoryUtil.memAlloc(vbSize).order(ByteOrder.nativeOrder());
        int ibSize = totalIndices * Integer.BYTES;
        ByteBuffer ib = MemoryUtil.memAlloc(ibSize).order(ByteOrder.nativeOrder());
        try {
            for (int i = 0; i < meshCount; i++) {
                packVertices(meshes[i], vb);
            }
            vb.flip();
            for (int i = 0; i < meshCount; i++) {
                packIndices(meshes[i], ib);
            }
            ib.flip();
            vertexBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "Golem flywheel vertex pool", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vb);
            indexBuffer = RenderSystem.getDevice()
                    .createBuffer(() -> "Golem flywheel index pool", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, ib);
        } finally {
            MemoryUtil.memFree(vb);
            MemoryUtil.memFree(ib);
        }
        dirty = false;
    }

    private static void packVertices(Mesh mesh, ByteBuffer buffer) {
        float[] data = mesh.data;
        if (data == null) return;
        int floats = mesh.vertexCount() * 8;
        for (int off = 0; off < floats; off += 8) {
            Layout.put(
                    buffer,
                    data[off], data[off + 1], data[off + 2],
                    data[off + 3], data[off + 4],
                    data[off + 5], data[off + 6], data[off + 7]
            );
        }
    }

    private static void packIndices(Mesh mesh, ByteBuffer buffer) {
        int[] indices = mesh.indices;
        if (indices == null) return;
        int count = mesh.indexCount();
        for (int i = 0; i < count; i++) {
            buffer.putInt(indices[i]);
        }
    }

    private static void ensureMeshCapacity(int required) {
        if (required <= meshes.length) return;
        int newCapacity = meshes.length << 1;
        while (newCapacity < required) {
            newCapacity <<= 1;
        }
        meshes = Arrays.copyOf(meshes, newCapacity);
    }
}
//?}
