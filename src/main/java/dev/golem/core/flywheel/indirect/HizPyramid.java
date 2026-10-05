package dev.golem.core.flywheel.indirect;

//? if >=26.1 {

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

// Hierarchical depth pyramid for the INDIRECT backend's occlusion cull. Owns a single
// R32F texture with a full mip chain sized to the next-power-of-two ≤ window dimensions
// (mip 0 covers the screen at half-resolution worst case). Rebuilt once per dispatchCullPass
// from the vanilla MainRenderTarget depth attachment via the two compute shaders in
// IndirectHizProgram (first pass + per-mip downsample). Cull samples it as sampler2D bound
// to texture unit 0 — see golem_cull.csh for the projection-into-pyramid test.
//
// Texture format & filtering:
//   - GL_R32F, mipmapped, linear-magnify / linear-minify-linear (we sample by texelFetch so
//     filtering is irrelevant at runtime, but a no-op default keeps Mojang's validators happy).
//   - GL_NEAREST/CLAMP_TO_EDGE on the depth source — out-of-range gathers return 0 which is
//     the cleared depth, which the conservative max keeps as the smallest depth seen.
//
// Resize strategy: re-allocate the whole texture on window-size change. The window check is
// keyed on framebuffer width/height — if either differs from the recorded value we tear down
// and rebuild. No partial reuse.
//
// Texture binds go through GlStateManager, never raw glActiveTexture/glBindTexture. blaze3d keeps
// a per-unit cache of the bound texture and skips the GL call when the cache says the texture is
// already bound; a raw bind leaves that cache naming the texture it replaced, so the next cached
// "bind" of that texture is skipped and the parameters that follow it land on the pyramid. With
// Sodium 0.9 on 26.1.2 that happened every few frames (2026-10-05, History §19em-hiz): the pyramid's
// GL_TEXTURE_MAX_LEVEL was reset to 0, textureSize() in the cull read 0 for every mip above 0, and
// every instance tested as occluded — machines drew nothing but their shadows until a backend
// reset generated a new texture.
public final class HizPyramid {

    private int textureId;
    private int width;
    private int height;
    private int mipLevels;

    public boolean ensureSized(int targetWidth, int targetHeight) {
        RenderSystem.assertOnRenderThread();
        if (targetWidth <= 0 || targetHeight <= 0) return false;
        int desiredW = mip0Size(targetWidth);
        int desiredH = mip0Size(targetHeight);
        if (textureId != 0 && desiredW == width && desiredH == height) return true;
        close();
        textureId = GlStateManager._genTexture();
        // GL_TEXTURE_2D is the conventional binding target for sampler2D + image2D in the
        // pyramid build / sample paths.
        bind(0, textureId);
        int levels = mipLevels(desiredW, desiredH);
        GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, levels, GL30.GL_R32F, desiredW, desiredH);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels - 1);
        width = desiredW;
        height = desiredH;
        mipLevels = levels;
        return true;
    }

    public int textureId() {
        return textureId;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int mipLevels() {
        return mipLevels;
    }

    public void rebuild(GpuTexture depthTexture, IndirectHizProgram program) {
        RenderSystem.assertOnRenderThread();
        if (textureId == 0 || depthTexture == null) return;
        if (!program.ensureLoaded()) return;

        int depthHandle = ((GlTexture) depthTexture).glId();

        // First pass: vanilla depth → mip 0. Bind depth to sampler unit 0, mip 0 to image unit 0.
        program.useFirst();
        bind(0, depthHandle);
        // textureGather wants nearest filtering and the comparison mode disabled — Mojang may
        // have left GL_TEXTURE_COMPARE_MODE on GL_COMPARE_REF_TO_TEXTURE for shadow sampling
        // elsewhere, which would make gather return comparison results instead of raw depth.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL42.glBindImageTexture(0, textureId, 0, false, 0, GL43.GL_WRITE_ONLY, GL30.GL_R32F);
        program.setMip0Size(width, height);
        GL43.glDispatchCompute(groupCount(width), groupCount(height), 1);
        GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);

        // Subsequent mips: each pass reads from the previous mip and writes to the current one.
        program.useDown();
        int curW = width;
        int curH = height;
        for (int level = 1; level < mipLevels; level++) {
            int dstW = Math.max(curW >> 1, 1);
            int dstH = Math.max(curH >> 1, 1);
            GL42.glBindImageTexture(0, textureId, level - 1, false, 0, GL43.GL_READ_ONLY, GL30.GL_R32F);
            GL42.glBindImageTexture(1, textureId, level,     false, 0, GL43.GL_WRITE_ONLY, GL30.GL_R32F);
            program.setDownDstSize(dstW, dstH);
            GL43.glDispatchCompute(groupCount(dstW), groupCount(dstH), 1);
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
            curW = dstW;
            curH = dstH;
        }
        // Final barrier so the cull pass's sampler2D fetch sees fully-built mips.
        GL42.glMemoryBarrier(GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
    }

    public void close() {
        if (textureId != 0) {
            GlStateManager._deleteTexture(textureId);
            textureId = 0;
        }
        width = 0;
        height = 0;
        mipLevels = 0;
    }

    /** Binds {@code texture} to GL_TEXTURE_2D of {@code unit} through blaze3d's state cache (see the class comment). */
    static void bind(int unit, int texture) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(texture);
    }

    // Mip 0 covers the framebuffer at half-resolution snapped down to next power-of-two.
    // Half-res keeps the pyramid memory bounded (~2 MB for 1080p) and matches upstream
    // Flywheel's DepthPyramid.mip0Size convention.
    private static int mip0Size(int dim) {
        if (dim <= 1) return 1;
        int v = dim >> 1;
        // Round up to next power-of-two so the chain halves cleanly without odd-mip rounding.
        int p = 1;
        while (p < v) p <<= 1;
        return p;
    }

    private static int mipLevels(int w, int h) {
        int max = Math.max(w, h);
        int levels = 1;
        while (max > 1) {
            max >>= 1;
            levels++;
        }
        return levels;
    }

    private static int groupCount(int dim) {
        return (dim + 7) / 8;
    }
}
//?}
