package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import dev.golem.core.hook.Verdict;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Whether water emptied from a bucket evaporates where it lands, at a position: vanilla's "water evaporates" rule
 * (the Nether's {@code ultraWarm} dimension type before 26.1, the {@code water_evaporates} environment attribute on
 * 26.1), which knows the dimension or biome but not the place. A mod whose air can vanish locally (a vacuum outside a
 * sealed room on a moon) answers here.
 *
 * <p>Asked by Golem Core's {@code MixinBucketEvaporation} inside vanilla's {@code BucketItem.emptyContents}, which both a
 * player's bucket and a dispenser's water bucket go through. {@link Verdict#ALLOW} evaporates the water (vanilla's hiss
 * and smoke, the bucket is emptied, nothing is placed), {@link Verdict#DENY} places it even where vanilla would
 * evaporate it, {@link Verdict#PASS} keeps vanilla's answer. A loader's own fluid-type vaporisation (NeoForge and Forge
 * ask the fluid type first, for a stack-aware bucket) still runs before this rule and is not overridden by a DENY.
 */
public final class Evaporation {

    private Evaporation() {}

    /** A listener's answer to "does water placed at {@code pos} evaporate?". */
    @FunctionalInterface
    public interface Rule {
        Verdict evaporates(Level level, BlockPos pos);
    }

    /** Evaporation answers; the first that does not pass decides, nothing deciding keeps vanilla's answer. */
    public static final Hook<Rule> WATER = Hook.of(listeners -> (level, pos) ->
            Verdict.first(listeners, listener -> listener.evaporates(level, pos)));

    /** Whether water placed at {@code pos} evaporates, given vanilla's answer {@code vanilla}. */
    public static boolean water(Level level, BlockPos pos, boolean vanilla) {
        if (WATER.isEmpty()) return vanilla;
        return WATER.invoker().evaporates(level, pos).orElse(vanilla);
    }
}
