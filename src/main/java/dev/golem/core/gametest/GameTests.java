package dev.golem.core.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

//? if >=26.1 {
import dev.golem.core.registry.DeferredRegister;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
//?} else {
/*import net.minecraft.gametest.framework.TestFunction;
import java.util.ArrayList;
import java.util.List;
*///?}

/**
 * GameTests written once, registered on whichever framework the target has.
 *
 * <p>The two frameworks share only {@code GameTestHelper}, which is why the test bodies port unchanged
 * and everything around them does not. 26.1 is data-driven: a body is a registry entry in
 * {@code Registries.TEST_FUNCTION}, and a test is a {@code FunctionGameTestInstance} built from a
 * {@code TestData} naming an environment and a structure. 1.21.1 predates all of that — tests are static
 * methods annotated {@code @GameTest}, discovered by class — and has no test-function registry at all.
 *
 * <p>The bridge below 26.1 is {@code @GameTestGenerator}: a method that returns {@code TestFunction}s
     * built at runtime, which is the one place that era accepts programmatically-defined tests. So a mod
     * declares its bodies and budgets together, and neither declaration mentions an era.
 *
 * <h2>Two traps this hides</h2>
 * <ul>
 *   <li><b>Namespace filtering.</b> With {@code neoforge.enabledGameTestNamespaces} set — which the
 *       {@code runGameTestServer} task does — 1.21.1 filters generated tests by the namespace of their
 *       <em>structure</em>, not their name. The structure is therefore {@code <namespace>:empty}, and the
 *       mod must ship it; a structure in this library's namespace would see every test silently dropped.</li>
 *   <li><b>The structure must exist.</b> 26.1 has {@code minecraft:empty} built in; 1.21.1 does not, and a
 *       missing template fails the run with "Missing test structure". A 1×1×1 air template is enough — the
 *       tests build their own scenery — and is what {@code minecraft:empty} is on 26.1.</li>
 * </ul>
 */
public final class GameTests {

    private GameTests() {}

    /** The structure every test uses: a 1×1×1 air template the test then builds its own scenery in. */
    private static final String EMPTY_STRUCTURE = "empty";

    /** A named test body and its execution budget, declared once and registered per target. */
    public static final class Function {

        private final String namespace;
        private final String name;
        private final int timeoutTicks;
        private final int padding;
        private final Supplier<Consumer<GameTestHelper>> body;

        private Function(String namespace, String name, int timeoutTicks, int padding,
                         Supplier<Consumer<GameTestHelper>> body) {
            this.namespace = namespace;
            this.name = name;
            this.timeoutTicks = timeoutTicks;
            this.padding = padding;
            this.body = body;
        }

        /** {@code namespace:name}, the id this test is reported under on both eras. */
        public String id() {
            return namespace + ":" + name;
        }
    }

    /** An ordered collection of a mod's tests, so loaders only need to forward their event once. */
    public static final class Suite {

        private final String namespace;
        private final List<Function> functions = new ArrayList<>();

        private Suite(String namespace) {
            this.namespace = namespace;
        }

        public Function function(String name, int timeoutTicks, Supplier<Consumer<GameTestHelper>> body) {
            return function(name, timeoutTicks, 0, body);
        }

        /**
         * Declares a test that needs elbow room. The empty template every test starts from is 1×1×1, so
         * the runner packs test plots tightly; a test that builds a large structure around itself will
         * otherwise write straight into its neighbour's plot — which shows up as the <i>neighbour</i>
         * failing, not this one. {@code padding} is the free space (in blocks) reserved around this
         * test's plot.
         */
        public Function function(String name, int timeoutTicks, int padding,
                                 Supplier<Consumer<GameTestHelper>> body) {
            Function function = GameTests.function(namespace, name, timeoutTicks, padding, body);
            functions.add(function);
            return function;
        }
    }

    //? if >=26.1 {
    /**
     * One test-function register per namespace. 26.1 needs the bodies in a real registry so a test can
     * reference one by {@link ResourceKey}; this era-only detail is why {@link #function} exists rather
     * than callers holding their own register.
     */
    private static final Map<String, DeferredRegister<Consumer<GameTestHelper>>> REGISTERS = new HashMap<>();

    /** Environments are registered once per namespace, on the first test that needs one. */
    private static final Map<String, Holder<TestEnvironmentDefinition<?>>> ENVIRONMENTS = new HashMap<>();
    //?} else {
    /*/^* Tests awaiting pickup by the generator; see {@link GolemTestGenerator}. ^/
    private static final List<TestFunction> PENDING = new ArrayList<>();

    /^* Whether the generator class has been handed to the loader yet. ^/
    private static boolean generatorRegistered;
    *///?}

    /**
     * Declares a test body. Call from a static initialiser, exactly as a registry entry would be
     * declared — on 26.1 this really is one.
     */
    public static Suite suite(String namespace) {
        return new Suite(namespace);
    }

    public static Function function(String namespace, String name, int timeoutTicks,
                                    Supplier<Consumer<GameTestHelper>> body) {
        return function(namespace, name, timeoutTicks, 0, body);
    }

    public static Function function(String namespace, String name, int timeoutTicks, int padding,
                                    Supplier<Consumer<GameTestHelper>> body) {
        //? if >=26.1 {
        REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(Registries.TEST_FUNCTION, ns))
                .register(name, body);
        //?}
        return new Function(namespace, name, timeoutTicks, padding, body);
    }

    //? if neoforge {
    /**
     * Registers a declared body as a runnable test. Call from NeoForge's {@code RegisterGameTestsEvent}.
     */
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event,
                                 Suite suite) {
        for (Function function : suite.functions) {
            //? if >=26.1 {
            Holder<TestEnvironmentDefinition<?>> environment = ENVIRONMENTS.computeIfAbsent(
                    function.namespace,
                    ns -> event.registerEnvironment(dev.golem.core.Ids.of(ns, "test_environment"),
                            new TestEnvironmentDefinition.Functions(Optional.empty(), Optional.empty())));

            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                    environment, net.minecraft.resources.Identifier.withDefaultNamespace(EMPTY_STRUCTURE),
                    function.timeoutTicks, 0, true, Rotation.NONE, false, 1, 1, false, function.padding);

            ResourceKey<Consumer<GameTestHelper>> key =
                    ResourceKey.create(Registries.TEST_FUNCTION, dev.golem.core.Ids.of(function.namespace, function.name));
            event.registerTest(dev.golem.core.Ids.of(function.namespace, function.name),
                    testData -> new FunctionGameTestInstance(key, testData), data);
            //?} else {
            /*// The structure is namespaced to the MOD, not to this library: generated tests are filtered by
        // their structure's namespace when enabledGameTestNamespaces is set, so a golemcore structure
        // would see every one of them silently dropped.
        PENDING.add(new TestFunction(
                "defaultBatch",
                function.id(),
                function.namespace + ":" + EMPTY_STRUCTURE,
                function.timeoutTicks,
                0L,
                true,
                function.body.get()));

        if (!generatorRegistered) {
            generatorRegistered = true;
            event.register(GolemTestGenerator.class);
        }
            *///?}
        }
    }
    //?} else if forge {
    /*public static void register(net.minecraftforge.event.RegisterGameTestsEvent event,
                                  Suite suite) {
        for (Function function : suite.functions) {
            PENDING.add(new TestFunction(
                "defaultBatch",
                function.id(),
                function.namespace + ":" + EMPTY_STRUCTURE,
                function.timeoutTicks,
                0L,
                true,
                function.body.get()));

            if (!generatorRegistered) {
                generatorRegistered = true;
                event.register(GolemTestGenerator.class);
            }
        }
    }
        *///?}

    //? if <26.1 {
    /*/^* The tests collected so far, for the generator to hand to the framework. ^/
    static List<TestFunction> pending() {
        return PENDING;
    }
    *///?}
}
