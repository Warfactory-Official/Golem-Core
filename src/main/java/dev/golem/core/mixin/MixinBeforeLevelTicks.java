package dev.golem.core.mixin;

import dev.golem.core.platform.Lifecycle;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla has no ticket-to-level-tick callback. Fabric exposes this phase; NeoForge lacks it.
 * Widening cannot add a phase, so dispatch immediately before native level ticks.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinBeforeLevelTicks {
    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void golem$beforeLevelTicks(BooleanSupplier shouldKeepTicking, CallbackInfo callback) {
        Lifecycle.beforeLevelTicks((MinecraftServer) (Object) this);
    }
}
