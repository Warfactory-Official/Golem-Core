package dev.golem.core.mixin;

import dev.golem.core.world.FloatingTickCounter;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the pre-26.1 anti-floating reset without relying on loader-specific AT name spaces.
 *
 * <p>26.1 has a public vanilla reset method; older targets keep only this private counter and
 * provide no event or facade hook, so this is the smallest remaining bridge for those targets.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListener implements FloatingTickCounter {

    @Shadow
    private int aboveGroundTickCount;

    @Override
    public void resetFloatingTickCount() {
        aboveGroundTickCount = 0;
    }
}
