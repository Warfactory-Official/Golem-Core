package dev.golem.core.mixin;

import dev.golem.core.platform.Lifecycle;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla has no callback between TickRateManager.tick and the level ticks. Every loader's start-of-tick event
 * (Fabric START_SERVER_TICK, NeoForge ServerTickEvent.Pre, Forge Phase.START) fires before TickRateManager.tick,
 * and widening cannot add a phase, so dispatch at the head of tickChildren.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinBeforeLevelTicks {
    @Inject(method = "tickChildren", at = @At("HEAD"))
    private void golem$beforeLevelTicks(BooleanSupplier shouldKeepTicking, CallbackInfo callback) {
        Lifecycle.beforeLevelTicks((MinecraftServer) (Object) this);
    }
}
