package dev.golem.core.platform;

/**
 * A mod built on Golem Core. Golem Core owns the loader wiring: registries, payloads, GameTest suites and
 * client registrations declared through its facades are flushed for every Golem mod at the loader's moment.
 *
 * <p>Entry: Fabric lists the class under the {@code golem} entrypoint key; Golem Core runs all of them from
 * its own {@code main}, dependencies first. Forge-like loaders construct an {@code @Mod} class that calls
 * {@link GolemMods#boot}.
 */
public interface GolemMod {

    /**
     * Declares registers, payloads, suites, lifecycle hooks, content phases and subscriptions
     * ({@link dev.golem.core.content.Pipeline}). Before any registry flush.
     */
    void init();

    /** After every registry has flushed. */
    default void setup() {}

    /** Physical client only, after {@link #init}. Screens and renderers via {@code ClientRegistry}. */
    default void initClient() {}
}
