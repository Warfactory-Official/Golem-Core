package com.wf.gemrender.render;

import com.wf.gemrender.debug.SamplerProbe;
import com.wf.gemrender.particle.ParticleBuffer;
import com.wf.gemrender.texture.PaintArray;
import com.wf.gemrender.volume.Volumetrics;

public final class FrameUploads {

    private FrameUploads() {
    }

    public static void run() {
        SamplerProbe.sample();
        long uploadStart = System.nanoTime();

        GlAudit.Scope audit = GlAudit.open("gemrender:upload");
        try {
            BoneBuffer.getInstance()
                    .uploadAndBind();
            MorphBuffer.getInstance()
                    .uploadAndBind();
            ParticleBuffer.getInstance()
                    .uploadAndBind();
            Volumetrics.getInstance()
                    .upload();
            PaintArray.bind();
        } finally {
            audit.close();
        }

        FrameCost.getInstance()
                .addUploadNanos(System.nanoTime() - uploadStart);
        PoseCache.getInstance()
                .endFrame();
        FrameCost.getInstance()
                .endFrame();
    }
}
