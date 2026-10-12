package dev.golem.core.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Vanilla's hovered-block outline, skipped while any registered condition holds: 1.7's
 * {@code DrawBlockHighlightEvent#setCanceled(true)}, for a mod that draws its own highlight instead.
 *
 * <p>Client only. Each loader spells the hook differently: NeoForge 26.1 cancels the outline's extraction
 * ({@code ExtractBlockOutlineRenderStateEvent}), NeoForge 21.1 and Forge 47 cancel
 * {@code RenderHighlightEvent.Block}, and Fabric answers {@code false} from {@code BEFORE_BLOCK_OUTLINE}
 * ({@code LevelRenderEvents} from 26.1, {@code WorldRenderEvents} before).
 */
public final class BlockOutlines {

    /** Every listener, in phase order; register through the {@code on...} method or directly, with a phase. */
    public static final dev.golem.core.hook.Hook<BooleanSupplier> SUPPRESSORS = dev.golem.core.hook.Hook.notifying();
    private static boolean registered;

    private BlockOutlines() {}

    /** Skips vanilla's block outline on every frame {@code condition} answers true. */
    public static void suppressWhen(BooleanSupplier condition) {
        SUPPRESSORS.register(condition);
        register();
    }

    private static boolean suppressed() {
        for (BooleanSupplier condition : SUPPRESSORS) if (condition.getAsBoolean()) return true;
        return false;
    }

    private static void register() {
        if (registered) return;
        registered = true;
        //? if neoforge && >=26.1 {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent event) -> {
                    if (suppressed()) event.setCanceled(true);
                });
        //?} elif fabric && >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, outline) -> !suppressed());
        *///?} elif neoforge {
        /*net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.RenderHighlightEvent.Block event) -> {
                    if (suppressed()) event.setCanceled(true);
                });
        *///?} elif forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.RenderHighlightEvent.Block event) -> {
                    if (suppressed()) event.setCanceled(true);
                });
        *///?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_BLOCK_OUTLINE
                .register((context, hit) -> !suppressed());
        *///?}
    }
}
