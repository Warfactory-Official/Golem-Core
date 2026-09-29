package dev.golem.core.mixin;

import dev.golem.core.render.ScreenEffects;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// HBM 1.12 composited its bloom and heat-distortion buffers once the first-person hand was drawn and before the
// GUI. That point is the end of GameRenderer.renderLevel on every supported version (the hand is its last draw).
// Rung 1: vanilla has no callback there. Rung 2: Fabric has no hand or post-hand render event on any version;
// Forge/NeoForge only have RenderHandEvent, which fires before each hand, and their GUI events are too late
// (after entity outlines and post chains, and on 26.1 GUI extraction runs before the level renders at all).
// Rung 3: an access widener cannot add a timing point. One TAIL inject, no descriptor, same on all six nodes.
@Mixin(GameRenderer.class)
public class MixinScreenEffects {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void golem$compositeScreenEffects(CallbackInfo ci) {
        ScreenEffects.afterHand();
    }
}
