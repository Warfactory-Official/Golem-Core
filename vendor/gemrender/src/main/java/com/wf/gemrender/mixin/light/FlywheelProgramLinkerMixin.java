package com.wf.gemrender.mixin.light;

import com.wf.gemrender.light.LightShaders;
import dev.engine_room.flywheel.backend.compile.core.ProgramLinker;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ProgramLinker.class, remap = false)
abstract class FlywheelProgramLinkerMixin {
    @Inject(method = "link", at = @At("RETURN"))
    private void gemrender$bindLights(CallbackInfoReturnable<GlProgram> cir) {
        LightShaders.bindProgram(cir.getReturnValue()
                .handle());
    }
}
