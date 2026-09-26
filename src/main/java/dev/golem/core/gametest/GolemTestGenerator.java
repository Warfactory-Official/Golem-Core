package dev.golem.core.gametest;

/**
 * The one hook before 26.1 for tests that are not static annotated methods, and Golem Core's
 * {@code fabric-gametest} entrypoint on every Fabric node.
 *
 * <p>Before 1.21.4 a test is discovered by reflection: a {@code @GameTest} method on a registered class.
 * That cannot express a test whose body is a value rather than a method, which is what the 26.1 API this
 * port is written against uses. {@code @GameTestGenerator} is the exception — the framework calls it and
 * takes whatever {@code TestFunction}s it returns — so every test {@link GameTests} collected arrives
 * through here. On 26.1 the class is empty: tests are registry entries there.
 *
 * <p>Public by necessity: the framework reflects over this class.
 */
public final class GolemTestGenerator {

    public GolemTestGenerator() {}

    //? if <26.1 {
    /*@net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> generate() {
        //? if fabric {
        /^dev.golem.core.platform.GolemMods.fabricMain();
        ^///?}
        return GameTests.pending();
    }
    *///?}
}
