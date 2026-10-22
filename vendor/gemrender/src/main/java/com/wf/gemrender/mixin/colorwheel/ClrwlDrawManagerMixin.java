package com.wf.gemrender.mixin.colorwheel;

import com.wf.gemrender.volume.Volumetrics;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"dev.djefrey.colorwheel.indirect.ClrwlIndirectDrawManager",
        "dev.djefrey.colorwheel.instancing.ClrwlInstancedDrawManager"}, remap = false)
abstract class ClrwlDrawManagerMixin {

    @Inject(method = "renderTranslucent", at = @At("HEAD"))
    private void gemrender$beginVolumetrics(IrisRenderingPipeline pipeline, boolean isShadow,
                                            CallbackInfo ci) {
        if (!isShadow) {
            Volumetrics.getInstance()
                    .beginFrame();
        }
    }
}
