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
 * {@code TestData} naming an environment and a structure. 1.21.1 predates all of that: tests are static
 * methods annotated {@code @GameTest}, discovered by class, and has no test-function registry at all.
 *
 * <p>The bridge below 26.1 is {@code @GameTestGenerator}: a method that returns {@code TestFunction}s
 * built at runtime, the one place that era accepts programmatically defined tests. A mod declares its
 * bodies and budgets together, and neither declaration mentions an era.
 *
 * <h2>Two traps this hides</h2>
 * <ul>
 *   <li><b>Namespace filtering.</b> With {@code neoforge.enabledGameTestNamespaces} set (as the
 *       {@code runGameTestServer} task does), 1.21.1 filters generated tests by the namespace of their
 *       <em>structure</em>, not their name. The structure is therefore {@code <namespace>:empty}, and the
 *       mod must ship it; a structure in this library's namespace would see every test silently dropped.</li>
 *   <li><b>The structure must exist.</b> 26.1 has {@code minecraft:empty} built in; 1.21.1 does not, and a
 *       missing template fails the run with "Missing test structure". A 1×1×1 air template is enough: the
 *       tests build their own scenery. It is what {@code minecraft:empty} is on 26.1.</li>
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
         * the runner packs test plots tightly; a large structure would otherwise write into the neighbour's
         * plot and fail the <i>neighbour</i>. {@code padding} is the free space (in blocks) reserved
         * around this test's plot.
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

    /** The ids of {@link #FUNCTIONS}: the tests {@link TestChunks} holds until their plot is entity-ticking. */
    private static final java.util.Set<String> DECLARED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Environments, one per namespace <b>per registry load</b>, keyed by the event, not held statically.
     *
     * <p>{@code RegisterGameTestsEvent} fires on every {@code RegistryDataLoader.load}, and each load builds
     * <i>fresh</i> test-environment and test-instance registries. A namespace-keyed cache would hand the
     * second load a {@link Holder} from the first load's registry; the failure then surfaces far from here,
     * when joining a world throws {@code Failed to serialize ResourceKey[minecraft:test_instance / …]:
     * Element … is not valid in current registry set} while the server packs registries for the client.
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
     * declared; on 26.1 this really is one.
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
        Function function = new Function(namespace, name, timeoutTicks, padding, body);
        //? if >=26.1 {
        REGISTERS.computeIfAbsent(namespace, ns -> DeferredRegister.create(Registries.TEST_FUNCTION, ns))
                .register(name, body);
        FUNCTIONS.add(function);
        DECLARED.add(function.id());
        TestChunks.declare();
        //?}
        return function;
    }

    //? if >=26.1 {
    /** Whether {@code id} ({@code namespace:name}) is a test declared here. */
    static boolean declares(String id) {
        return DECLARED.contains(id);
    }
    //?}

    /**
     * The middle of the free space a test declared with {@code padding} owns, in the helper's relative
     * coordinates, at relative height {@code y}. A test that needs room on every side (a random jump, a blast
     * radius) centres itself here and stays within {@code padding} blocks of it.
     *
     * <p>26.1 reserves {@code padding} blocks on every side of its 1×1×1 template, so the centre is the origin.
     * Below 26.1 the room is the padded empty template itself (24, 80, 152 or 184 wide; see {@code addLegacy}),
     * which starts at the origin and grows in +x/+z, so the centre is half its width. One era's centre on
     * the other puts the test in a neighbour's plot.
     */
    public static net.minecraft.core.BlockPos paddedCentre(int padding, int y) {
        //? if >=26.1 {
        return new net.minecraft.core.BlockPos(0, y, 0);
        //?} else {
        /*int width = padding > 64 ? 184 : padding > 28 ? 152 : padding > 8 ? 80 : 24;
        return new net.minecraft.core.BlockPos(width / 2, y, width / 2);
        *///?}
    }

    /**
     * The helper-relative position of an absolute one: the exact inverse of {@code helper.absolutePos}, as 26.1's
     * {@code GameTestHelper.relativePos} is. Below 26.1 vanilla's {@code relativePos} rotates the offset by the test's
     * rotation turned another 180 degrees, so on an unrotated test it negates x and z. Here the inverse is built from
     * {@code absolutePos} itself (origin plus a quarter-turn multiple, whose inverse is the same turn applied three
     * times), so it holds for every rotation.
     */
    public static net.minecraft.core.BlockPos relativePos(GameTestHelper helper, net.minecraft.core.BlockPos absolute) {
        //? if >=26.1 {
        return helper.relativePos(absolute);
        //?} else {
        /*net.minecraft.core.BlockPos origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        net.minecraft.core.BlockPos offset = absolute.subtract(origin);
        for (int i = 0; i < 3; i++) offset = helper.absolutePos(offset).subtract(origin);
        return offset;
        *///?}
    }

    /**
     * Holds the chunks under {@code origin + (minX..maxX, minZ..maxZ)} (blocks, relative to the test's origin)
     * entity-ticking, generating them and the ring around them, until the returned action runs. On 26.1 every test's
     * own padded plot is already held before it starts; older versions only tick the chunks near the plot's corner, so a
     * test whose entity sits at the padded centre or leaves its plot needs a hold. Holds are counted per chunk, so
     * tests that share a chunk never release it from under each other; on 26.1 a hold also ends with its test.
     */
    public static Runnable holdChunks(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        net.minecraft.core.BlockPos origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        return TestChunks.hold(helper.getLevel(), origin, (origin.getX() + minX) >> 4, (origin.getX() + maxX) >> 4,
                (origin.getZ() + minZ) >> 4, (origin.getZ() + maxZ) >> 4);
    }

    /** Whether every chunk under {@code origin + (minX..maxX, minZ..maxZ)} (as {@link #holdChunks}) is entity-ticking. */
    public static boolean chunksTicking(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        net.minecraft.core.BlockPos origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        return TestChunks.ticking(helper.getLevel(), (origin.getX() + minX) >> 4, (origin.getX() + maxX) >> 4,
                (origin.getZ() + minZ) >> 4, (origin.getZ() + maxZ) >> 4);
    }

    /**
     * {@code -Dgolem.gametest.filter=<regex>} runs only the tests whose id matches. Every era honours it
     * at test-instance registration; on 26.1 the body stays in the test-function registry either way,
     * because that registry is declared before the property could matter and an unused body is inert.
     */
    private static boolean selected(Function function) {
        String filter = System.getProperty("golem.gametest.filter");
        return filter == null || filter.isBlank() || function.id().matches(filter);
    }

    //? if neoforge {
    /**
     * Registers a declared body as a runnable test. Call from NeoForge's {@code RegisterGameTestsEvent}.
     */
    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event,
                                 Suite suite) {
        for (Function function : suite.functions) {
            //? if >=26.1 {
            if (!selected(function)) continue;
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
    /*public static void register(net.minecraftforge.event.RegisterGameTestsEvent event,
                                  Suite suite) {
        for (Function function : suite.functions) {
            addLegacy(function);

            if (!generatorRegistered) {
                generatorRegistered = true;
                event.register(GolemTestGenerator.class);
            }
        }
    }
        *///?}

    //? if fabric && <26.1 {
    /*/^*
     * Fabric has no registration event: the mod's {@code fabric-gametest} entrypoint calls this for every
     * suite from its {@code @GameTestGenerator}, then returns {@link #pending()}.
     ^/
    public static void register(Suite suite) {
        for (Function function : suite.functions) addLegacy(function);
    }

    /^*
     * The tests of {@code suite} alone, NOT added to {@link #pending()}: what a second {@code fabric-gametest}
     * generator returns (an optional integration's own entrypoint, such as a mod's Sable tests). Returning
     * {@code pending()} from a second generator would hand every other suite's tests over twice.
     ^/
    public static List<TestFunction> functionsOf(Suite suite) {
        List<TestFunction> functions = new ArrayList<>();
        for (Function function : suite.functions) addLegacy(function, functions);
        return functions;
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
            if (!selected(function)) continue;
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
        addLegacy(function, PENDING);
    }

    private static void addLegacy(Function function, List<TestFunction> into) {
        // Spacers follow their test through the filter.
        if (!selected(function)) return;
        String structure = function.namespace + ":" + EMPTY_STRUCTURE;
        if (function.padding > 64) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_184";
        else if (function.padding > 28) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_152";
        else if (function.padding > 8) structure = function.namespace + ":" + EMPTY_STRUCTURE + "_80";
        // The short TestFunction constructor hard-codes skyAccess=false. That is harmless for 26.1's
        // 1x1x1 minecraft:empty (its enclosure is outside the test body), but 1.21.1's legacy padding
        // templates are large so their synthetic roof/walls would land inside the test
        // scenery. Use the full constructor there to keep the fixture open. 1.20.1 has no sky-access
        // field; retain its original constructor and enclosure behavior for that node.
        if (function.padding > 8) {
            //? if >=1.21.1 {
            into.add(new TestFunction(
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
            /^into.add(new TestFunction(
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
        into.add(new TestFunction(
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
        /^into.add(new TestFunction(
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

    /^* The tests collected so far, for the generator to hand to the framework. ^/
    public static List<TestFunction> pending() {
        return PENDING;
    }
    *///?}
}
