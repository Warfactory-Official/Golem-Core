package dev.golem.core.rei;

//? if rei {

import dev.golem.core.viewer.Subtypes;

/**
 * A mod's REI common plugin: the menu-slot transfer target ({@link MenuSlotAccessor}) and, on REI 26, the mod's
 * {@link Subtypes} as item comparators. A mod subclasses
 * it once (loader glue: {@code @REIPluginCommon} on Forge-likes, the {@code rei_common} entrypoint on Fabric) and passes
 * its declarations; it is server-safe, as REI loads common plugins on a dedicated server too.
 *
 * <p>REI 26 moved {@code registerItemComparators} from every plugin onto {@code REICommonPlugin}; REI 12 and 16 have
 * no such interface, their common plugins are {@code REIServerPlugin}s, and their client plugins register comparators
 * themselves ({@link ReiClientPlugin} does). Below 26.1 this class is therefore a server plugin that registers only the slot
 * accessor, so the mod's
 * plugin classes and entrypoints are the same on every node.
 */
public abstract class ReiCommonPlugin implements
        //? if >=26.1 {
        me.shedaniel.rei.api.common.plugins.REICommonPlugin {
        //?} else {
        /*me.shedaniel.rei.api.common.plugins.REIServerPlugin {
        *///?}

    private final Subtypes.Declarations subtypes;

    protected ReiCommonPlugin(Subtypes.Declarations subtypes) {
        this.subtypes = subtypes;
    }

    /** The menu-slot transfer target {@link ReiClientPlugin}'s transfer handlers use ({@link MenuSlotAccessor}). */
    @Override
    public void registerSlotAccessors(me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessorRegistry registry) {
        MenuSlotAccessor.register(registry);
    }

    //? if >=26.1 {
    @Override
    public void registerItemComparators(me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry registry) {
        Rei.comparators(registry, subtypes);
    }
    //?}
}
//?}
