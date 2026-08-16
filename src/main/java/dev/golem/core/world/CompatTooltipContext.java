package dev.golem.core.world;

/**
 * Version-neutral tooltip context.
 *
 * <p>The vanilla context changed from a nullable {@code Level} to {@code Item.TooltipContext}; no
 * stable context data is shared by all supported versions yet, so this marker keeps consumer tooltip
 * overrides source-stable while leaving room for an API-once context later.
 */
public final class CompatTooltipContext {
    static final CompatTooltipContext INSTANCE = new CompatTooltipContext();

    private CompatTooltipContext() {
    }
}
