package dev.golem.core.harness.mixin;

import dev.golem.core.harness.HarnessPacing;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 1.20 has no tick-rate API or loader pacing hook; widening cannot replace its four fixed loop intervals.
 */
@Mixin(MinecraftServer.class)
public abstract class MixinHarnessServerClock {

    //? if <1.21 {
    /*@ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L), require = 4)
    private long golem$harnessServerMillis(long original) {
        return HarnessPacing.serverMillis();
    }
    *///?}
}
