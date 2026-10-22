package com.wf.gemrender.mixin.colorwheel;

import com.wf.gemrender.render.FrameUploads;
import dev.engine_room.flywheel.api.backend.RenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.djefrey.colorwheel.engine.ClrwlEngine", remap = false)
abstract class ClrwlEngineMixin {

    @Inject(method = "prepareFrame(Ldev/engine_room/flywheel/api/backend/RenderContext;)V",
            at = @At(value = "INVOKE", shift = At.Shift.AFTER,
                    target = "Ldev/djefrey/colorwheel/engine/ClrwlDrawManager;prepareFrame("
                            + "Ldev/engine_room/flywheel/backend/engine/LightStorage;"
                            + "Ldev/djefrey/colorwheel/engine/embed/EnvironmentStorage;)V"))
    private void gemrender$uploadBuffers(RenderContext context, CallbackInfo ci) {
        FrameUploads.run();
    }
}
