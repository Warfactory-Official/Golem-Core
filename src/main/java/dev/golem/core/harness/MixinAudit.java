package dev.golem.core.harness;

import dev.golem.core.GolemCore;
import dev.golem.core.client.ClientTicks;
import dev.golem.core.platform.Platform;
import org.spongepowered.asm.mixin.MixinEnvironment;

/**
 * Development: {@code -Dgolem.mixinAudit=true} applies every mixin after the first client tick, so a mixin whose
 * target loads late (a GameTest class, a rarely opened screen) fails at startup instead of in play.
 */
final class MixinAudit {

    private static boolean done;

    private MixinAudit() {}

    static void installIfRequested() {
        if (!Boolean.getBoolean("golem.mixinAudit") || !Platform.isDevelopment()) return;
        ClientTicks.onEndTick(() -> {
            if (done) return;
            done = true;
            GolemCore.LOG.info("[mixin audit] applying every mixin");
            MixinEnvironment.getCurrentEnvironment().audit();
            GolemCore.LOG.info("[mixin audit] finished");
        });
    }
}
