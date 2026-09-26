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
//? if fabric && >=26.1 {
/*import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.Identifier;
*///?}
//?} else {
/*import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
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

    private static final List<Suite> SUITES = new ArrayList<>();

    /** {@code -Dgolem.gametest.filter=<regex>}: only matching test ids run. */
    private static boolean included(Function function) {
        String filter = System.getProperty("golem.gametest.filter");
        return filter == null || filter.isBlank() || function.id().matches(filter);
    }

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

    /** An ordered collection of a mod's tests. Every suite is registered by Golem Core's own bootstrap. */
    public static final class Suite {

        private final String namespace;
        private final List<Function> functions = new ArrayList<>();

        private Suite(String namespace) {
            this.namespace = namespace;
            SUITES.add(this);
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

    /** Every declared function, used by loaders whose API provides no registration event. */
    private static final List<Function> FUNCTIONS = new ArrayList<>();

    /**
     * Environments, one per namespace <b>per registry load</b> — keyed by the event, not held statically.
     *
     * <p>This map used to be keyed by namespace alone, and that was a real bug with a confusing symptom.
     * {@code RegisterGameTestsEvent} fires on every {@code RegistryDataLoader.load}, and each load builds
     * <i>fresh</i> test-environment and test-instance registries. A namespace-keyed cache hands the
     * second load a {@link Holder} belonging to the first load's registry, so that load's test instances
     * reference an environment that is not in its own registry set — and the failure surfaces nowhere
     * near here: joining a world throws
     * {@code Failed to serialize ResourceKey[minecraft:test_instance / …]: Element … is not valid in
     * current registry set} while the server packs registries for the client.
     *
     * <p>Weak keys so a finished load's entry goes away with the event.
     */
    private static final Map<Object, Map<String, Holder<TestEnvironmentDefinition<?>>>> ENVIRONMENTS =
            new java.util.WeakHashMap<>();
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

    private static Function function(String namespace, String name, int timeoutTicks, int padding,
                                    Supplier<Consumer<GameTestHelper>> body) {
        Function function = new Function(namespace, name, timeoutTicks, padding, body);
        //? if >=26.1 {
        REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(Registries.TEST_FUNCTION, ns))
                .register(name, body);
        FUNCTIONS.add(function);
        //?}
        return function;
    }

    //? if neoforge {
    /** Every declared suite, from Golem Core's {@code RegisterGameTestsEvent} listener. */
    public static void registerAll(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        SUITES.forEach(suite -> register(event, suite));
    }

    private static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event, Suite suite) {
        for (Function function : suite.functions) {
            //? if >=26.1 {
            if (!included(function)) continue;
            // Per event: one environment per namespace within THIS registry load. See ENVIRONMENTS.
            Holder<TestEnvironmentDefinition<?>> environment = ENVIRONMENTS
                    .computeIfAbsent(event, e -> new HashMap<>())
                    .computeIfAbsent(function.namespace,
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
        addLegacy(function);

        if (!generatorRegistered) {
            generatorRegistered = true;
            event.register(GolemTestGenerator.class);
        }
            *///?}
        }
    }
    //?} else if forge {
    /*/^* Every declared suite, from Golem Core's {@code RegisterGameTestsEvent} listener. ^/
    public static void registerAll(net.minecraftforge.event.RegisterGameTestsEvent event) {
        SUITES.forEach(suite -> register(event, suite));
    }

    private static void register(net.minecraftforge.event.RegisterGameTestsEvent event, Suite suite) {
        for (Function function : suite.functions) {
            addLegacy(function);

            if (!generatorRegistered) {
                generatorRegistered = true;
                event.register(GolemTestGenerator.class);
            }
        }
    }
        *///?}

    //? if fabric && >=26.1 {
    /*/^*
     * Fabric's modern GameTest API has no public registration event for data-driven test instances.
     * Its own initializer registers annotated tests, then its registry-data hook supplies the fresh
     * dynamic registries. Register the same instances NeoForge creates from this neutral declaration
     * list when that hook hands us the current registry set.
     ^/
    @SuppressWarnings("unchecked")
    public static void registerFabricDynamicEntries(List<RegistryLoadTask<?>> loadTasks) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> registries = new java.util.IdentityHashMap<>(loadTasks.size());
        for (RegistryLoadTask<?> entry : loadTasks) {
            registries.put(entry.registry.key(), entry.registry);
        }

        Registry<GameTestInstance> testInstances = (Registry<GameTestInstance>) registries.get(Registries.TEST_INSTANCE);
        if (testInstances == null) return;
        Registry<TestEnvironmentDefinition<?>> environmentRegistry =
                (Registry<TestEnvironmentDefinition<?>>) java.util.Objects.requireNonNull(
                        registries.get(Registries.TEST_ENVIRONMENT));

        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (Function function : FUNCTIONS) {
            if (!included(function)) continue;
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(function.namespace,
                    namespace -> Registry.registerForHolder(environmentRegistry,
                            dev.golem.core.Ids.of(namespace, "test_environment"),
                            new TestEnvironmentDefinition.Functions(Optional.empty(), Optional.empty())));

            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                    environment, Identifier.withDefaultNamespace(EMPTY_STRUCTURE),
                    function.timeoutTicks, 0, true, Rotation.NONE, false, 1, 1, false, function.padding);
            ResourceKey<Consumer<GameTestHelper>> key = ResourceKey.create(
                    Registries.TEST_FUNCTION, dev.golem.core.Ids.of(function.namespace, function.name));
            Registry.register(testInstances, dev.golem.core.Ids.of(function.namespace, function.name),
                    new FunctionGameTestInstance(key, data));
        }
    }
    *///?}

    //? if <26.1 {
    /*/^*
     * Padding below 26.1. This framework spaces plots by the structure's own size + 5 and the origin is
     * always the template's corner, so a padded test gets a wider empty template (24 + 2 * padding, see
     * port-tools/gen_empty_structures.py) AND a non-required no-op spacer with the same template placed
     * right before it: the template keeps the next plot away, the spacer keeps the previous one away.
     * The stock 24-wide template with its 5-block gap already covers a padding of 8.
     ^/
    private static void addLegacy(Function function) {
        if (!included(function)) return;
        String structure = function.namespace + ":" + EMPTY_STRUCTURE;
        if (function.padding > 64) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_184";
        else if (function.padding > 28) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_152";
        else if (function.padding > 8) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_80";
        // The short TestFunction constructor hard-codes skyAccess=false. That is harmless for 26.1's
        // 1x1x1 minecraft:empty (its enclosure is outside the test body), but 1.21.1's legacy padding
        // templates are deliberately large so their synthetic roof/walls would land inside the test
        // scenery. Use the full constructor there to keep the fixture open. 1.20.1 has no sky-access
        // field; retain its original constructor and enclosure behavior for that node.
        if (function.padding > 8) {
            //? if >=1.21.1 {
            PENDING.add(new TestFunction(
                    "defaultBatch",
                    function.id() + "_spacer",
                    structure,
                    Rotation.NONE,
                    20,
                    0L,
                    false,
                    false,
                    1,
                    1,
                    true,
                    GameTestHelper::succeed));
            //?} else {
            /^PENDING.add(new TestFunction(
                    "defaultBatch",
                    function.id() + "_spacer",
                    structure,
                    Rotation.NONE,
                    20,
                    0L,
                    false,
                    1,
                    1,
                    GameTestHelper::succeed));^///?}
        }
        //? if >=1.21.1 {
        PENDING.add(new TestFunction(
                "defaultBatch",
                function.id(),
                structure,
                Rotation.NONE,
                function.timeoutTicks,
                0L,
                true,
                false,
                1,
                1,
                true,
                function.body.get()));
        //?} else {
        /^PENDING.add(new TestFunction(
                "defaultBatch",
                function.id(),
                structure,
                Rotation.NONE,
                function.timeoutTicks,
                0L,
                true,
                1,
                1,
                function.body.get()));^///?}
    }

    /^* Every declared suite's tests, for the generator to hand to the framework. ^/
    static List<TestFunction> pending() {
        //? if fabric {
        /^if (PENDING.isEmpty()) SUITES.forEach(suite -> suite.functions.forEach(GameTests::addLegacy));
        ^///?}
        return PENDING;
    }
    *///?}
}
