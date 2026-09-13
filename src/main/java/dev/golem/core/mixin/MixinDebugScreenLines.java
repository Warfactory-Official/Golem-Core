package dev.golem.core.mixin;

//? if fabric && >=26.1 {

import dev.golem.core.client.DebugLines;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends Golem's debug-screen lines on Fabric.
 *
 * <p>26.1 made the debug screen a registry of {@code DebugScreenEntry}, but its registration method is
 * private and fabric-api publishes no way in — its {@code debug} module covers debug <em>renderers</em>
 * and entity subscriptions, not screen entries. NeoForge fires an event for exactly this, so only this
 * loader needs bytecode.
 *
 * <p>The left column, where vanilla already keeps its chunk and entity render statistics.
 */
@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenLines {

    @Inject(method = "extractLines", at = @At("TAIL"))
    private void golemcore$appendLines(GuiGraphicsExtractor graphics, List<String> lines, boolean left,
                                       CallbackInfo info) {
        if (!left) return;
        DebugLines.collectInto(lines::add);
    }
}
//?}
