package dev.golem.core.gametest;

//? if <26.1 {

/*import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;

import java.util.Collection;

/^*
 * The one hook this era offers for tests that are not static annotated methods.
 *
 * <p>Before 1.21.4 a test is discovered by reflection: a {@code @GameTest} method on a registered class.
 * That cannot express a test whose body is a value rather than a method, which is what the 26.1 API this
 * port is written against uses. {@code @GameTestGenerator} is the exception — the framework calls it and
 * takes whatever {@code TestFunction}s it returns — so every test {@link GameTests} collected arrives
 * through here.
 *
 * <p>Public and non-final by necessity: the framework reflects over this class.
 ^/
public final class GolemTestGenerator {

    private GolemTestGenerator() {}

    @GameTestGenerator
    public static Collection<TestFunction> generate() {
        return GameTests.pending();
    }
}
*///?}
