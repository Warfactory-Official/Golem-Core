package dev.golem.core.mixin;

import dev.golem.core.client.Fogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;

/**
 * Vanilla exposes no event caller distance, and loader events omit it.
 * Widening cannot add per-invocation state; carry it on the same event until dispatch.
 */
@Pseudo
@Mixin(targets = {"net.neoforged.neoforge.client.event.ViewportEvent$RenderFog",
        "net.minecraftforge.client.event.ViewportEvent$RenderFog"}, remap = false)
public abstract class MixinRenderFogCallerDistance implements Fogs.CallerDistance {
    @Unique private float golem$callerDistance = Float.NaN;

    @Override public float golem$callerDistanceBlocks() { return golem$callerDistance; }

    @Override public void golem$callerDistanceBlocks(float blocks) {
        if (!Float.isFinite(blocks)) throw new IllegalArgumentException("Non-finite fog caller distance");
        golem$callerDistance = blocks;
    }
}
