package dev.golem.probe;

import dev.golem.core.platform.ModEntry;
import dev.golem.core.registry.Fuels;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A dev-only mod that exercises Golem Core without a content mod: a {@link Fuels} fuel, GameTests ({@code golemprobe:*}),
 * viewer and block-info content ({@link ProbeViewer}) and, in {@code ProbeClient}, a skybox and weather that draw only
 * while the player holds a debug stick. Never shipped.
 */
public final class ProbeEntry implements ModEntry {

    public static final String MOD_ID = "golemprobe";
    /** Paper is no vanilla fuel, so any burn time it has comes from Core. */
    public static final int FUEL_TICKS = 200;

    @Override
    public void declare() {
        ProbeGameTests.init();
        ProbeViewer.declareInfo();
    }

    @Override
    public void registered() {
        Map<Supplier<Item>, Integer> fuels = Map.of(() -> Items.PAPER, FUEL_TICKS);
        Fuels.register(fuels);
    }
}
