package dev.golem.core.platform;

/**
 * A mod's common start-up, split into the three phases every loader can honour in the same order for every mod
 * built on Golem Core (see {@link ModEntries}).
 *
 * <ol>
 *   <li>{@link #substitute()} - before any mod's registers are filled into the registries: replace another mod's
 *       entries.</li>
 *   <li>{@link #declare()} - fill this mod's registers and declare its payloads, GameTests and callbacks. Nothing in a
 *       registry exists yet.</li>
 *   <li>{@link #registered()} - every mod's registers are in the registries.</li>
 * </ol>
 *
 * <p>Golem Core fills the registries itself, once, from every mod's registers; a mod never flushes registers or
 * payloads.
 */
public interface ModEntry {

    /** Before any registry is filled. */
    default void substitute() {}

    /** Fill this mod's registers; declare payloads, GameTests and callbacks. */
    void declare();

    /** Every mod's registers are in the registries. */
    default void registered() {}
}
