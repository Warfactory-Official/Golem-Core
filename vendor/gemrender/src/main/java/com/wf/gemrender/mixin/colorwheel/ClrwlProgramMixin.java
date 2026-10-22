package com.wf.gemrender.mixin.colorwheel;

import com.google.common.collect.ImmutableSet;
import com.wf.gemrender.render.SamplerBindings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.djefrey.colorwheel.compile.ClrwlProgram", remap = false)
abstract class ClrwlProgramMixin {

    @Inject(method = "getReservedTextureUnits", at = @At("RETURN"), cancellable = true)
    private static void gemrender$reserveSamplerUnits(int coeffCount,
                                                      CallbackInfoReturnable<ImmutableSet<Integer>> cir) {
        ImmutableSet.Builder<Integer> reserved = ImmutableSet.builder();
        reserved.addAll(cir.getReturnValue());
        for (int unit : SamplerBindings.units()) {
            reserved.add(unit);
        }
        cir.setReturnValue(reserved.build());
    }
}
