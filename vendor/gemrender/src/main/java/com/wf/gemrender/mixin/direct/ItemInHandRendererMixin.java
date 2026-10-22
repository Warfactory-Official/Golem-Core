package com.wf.gemrender.mixin.direct;

import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if fabric {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.fabric.HandRenderEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    //? if fabric {
    /*@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void gemrender$beforeHand(float partialTick, PoseStack pose,
            MultiBufferSource.BufferSource buffers, LocalPlayer player, int light, CallbackInfo ci) {
        HandRenderEvents.BEFORE_HAND.invoker()
                .beforeHand(pose, buffers, light, partialTick);
    }
    *///?}

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void gemrender$flushDirect(CallbackInfo ci) {
        DirectRenderer.flush(DirectPass.HAND);
    }
}
