package com.wf.gemrender.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.renderer.MultiBufferSource;

public final class HandRenderEvents {

    public static final Event<BeforeHand> BEFORE_HAND = EventFactory.createArrayBacked(
            BeforeHand.class, listeners -> (pose, buffers, light, partialTick) -> {
                for (BeforeHand listener : listeners) {
                    listener.beforeHand(pose, buffers, light, partialTick);
                }
            });

    private HandRenderEvents() {
    }

    @FunctionalInterface
    public interface BeforeHand {
        void beforeHand(PoseStack pose, MultiBufferSource buffers, int light, float partialTick);
    }
}
