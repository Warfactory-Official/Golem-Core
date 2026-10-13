// SPDX-FileCopyrightText: 2021-2024 Jozufozu
// SPDX-License-Identifier: MIT
// Derived from Flywheel; see THIRD_PARTY_NOTICES.md.
package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

// Per-VisualizationManager UBO holding 0..MAX_LIGHTS point lights for Golem-rendered geometry.
// Lights are gathered each frame from ShaderLightVisuals via VisualizationManager.collectLights,
// converted to render-origin space (matches the vertex shader's renderOriginPos output) and
// uploaded via writeToBuffer outside any render pass. Both backends bind it once per pass via
// pass.setUniform("ShaderLights", slice()). Fragment shaders loop over LightCount and add
// quadratic-falloff contribution before fog.
//
// std140 layout (header + struct array):
//   offset 0   : int   LightCount
//   offset 16  : Lights[i].posRadius (xyz=position in render-origin-space, w=radius)  | 16 B
//   offset 32  : Lights[i].color     (xyz=color, w=unused)                            | 16 B
//   ...repeats per light, 32 B stride
public final class ShaderLightBuffer implements ShaderLightVisual.LightCollector {

    public static final int MAX_LIGHTS = 16;
    private static final int LIGHT_STRIDE = 32;
    private static final int HEADER_BYTES = 16;
    public static final int TOTAL_BYTES = HEADER_BYTES + MAX_LIGHTS * LIGHT_STRIDE;

    private GpuBuffer ubo;
    private ByteBuffer staging;
    private float originX;
    private float originY;
    private float originZ;
    private int count;

    public ShaderLightBuffer() {
        this.staging = MemoryUtil.memCalloc(TOTAL_BYTES).order(ByteOrder.nativeOrder());
    }

    public synchronized void update(VisualizationManager vm, double originX, double originY, double originZ) {
        RenderSystem.assertOnRenderThread();
        // Subtract origin in double precision before casting so the cancellation happens at full
        // double precision; matches Engine.buildFrameState's dx/dy/dz convention.
        this.originX = (float) originX;
        this.originY = (float) originY;
        this.originZ = (float) originZ;
        count = 0;
        // Wipe the entire staging range; tail slots beyond count must read as zero radius so a
        // shader bug that walks past LightCount discards via the radius check.
        MemoryUtil.memSet(MemoryUtil.memAddress(staging), 0, TOTAL_BYTES);
        vm.collectLights(this);
        staging.putInt(0, count);

        ensureBuffer();
        staging.position(0).limit(TOTAL_BYTES);
        RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToBuffer(ubo.slice(0L, TOTAL_BYTES), staging);
        staging.clear();
    }

    @Override
    public void accept(ShaderLight light) {
        if (count >= MAX_LIGHTS) return;
        int off = HEADER_BYTES + count * LIGHT_STRIDE;
        staging.putFloat(off,      light.x() - originX);
        staging.putFloat(off + 4,  light.y() - originY);
        staging.putFloat(off + 8,  light.z() - originZ);
        staging.putFloat(off + 12, light.radius());
        staging.putFloat(off + 16, light.r());
        staging.putFloat(off + 20, light.g());
        staging.putFloat(off + 24, light.b());
        // off + 28 is alignment padding; the wipe above keeps it zero.
        count++;
    }

    public synchronized GpuBufferSlice slice() {
        ensureBuffer();
        return ubo.slice(0L, TOTAL_BYTES);
    }

    private void ensureBuffer() {
        if (ubo == null) {
            ubo = RenderSystem.getDevice().createBuffer(
                    () -> "Golem ShaderLights",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    TOTAL_BYTES);
        }
    }

    public synchronized void close() {
        if (ubo != null) {
            ubo.close();
            ubo = null;
        }
        if (staging != null) {
            MemoryUtil.memFree(staging);
            staging = null;
        }
    }
}
//?}
