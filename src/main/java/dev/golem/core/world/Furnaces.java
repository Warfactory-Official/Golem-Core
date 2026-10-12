package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import dev.golem.core.hook.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Vanilla's furnace family (furnace, blast furnace, smoker) lighting from fuel, at a position. Vanilla and the loaders
 * offer only a fuel's burn time, which knows no place; a mod that makes fire depend on where it burns (no oxygen on a
 * moon) answers here.
 *
 * <p>Called by Golem Core's {@code MixinFurnaceIgnition} when a furnace that is not lit is about to take a fuel item:
 * a {@link Verdict#DENY} leaves it unlit and the fuel unconsumed, exactly as a fuel worth nothing would.
 */
public final class Furnaces {

    private Furnaces() {}

    /** A listener's answer to "may this furnace light from this fuel?". */
    @FunctionalInterface
    public interface Ignition {
        Verdict mayIgnite(Level level, BlockPos pos, ItemStack fuel);
    }

    /** Ignition answers; the first that does not pass decides, and nothing deciding lets the furnace light. */
    public static final Hook<Ignition> IGNITION = Hook.of(listeners -> (level, pos, fuel) ->
            Verdict.first(listeners, listener -> listener.mayIgnite(level, pos, fuel)));

    /** The burn time a furnace at {@code pos} gets from {@code fuel}: {@code duration}, or 0 when a listener denies. */
    public static int burnDuration(Level level, BlockPos pos, ItemStack fuel, int duration) {
        if (duration <= 0 || IGNITION.isEmpty()) return duration;
        return IGNITION.invoker().mayIgnite(level, pos, fuel) == Verdict.DENY ? 0 : duration;
    }
}
