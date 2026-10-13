package dev.golem.core.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class HookTest {

    private static Hook<Consumer<List<String>>> recording() {
        return Hook.of(listeners -> log -> listeners.forEach(listener -> listener.accept(log)));
    }

    private static List<String> run(Hook<Consumer<List<String>>> hook) {
        List<String> log = new ArrayList<>();
        hook.invoker().accept(log);
        return log;
    }

    @Test
    void defaultPhaseKeepsRegistrationOrder() {
        Hook<Consumer<List<String>>> hook = recording();
        hook.register(log -> log.add("a"));
        hook.register(log -> log.add("b"));
        assertEquals(List.of("a", "b"), run(hook));
    }

    @Test
    void declaredPhasesRunInOrder() {
        Hook<Consumer<List<String>>> hook = Hook.withPhases(listeners -> log -> listeners.forEach(l -> l.accept(log)),
                "hbm:early", Hook.DEFAULT_PHASE, "hbm:late");
        hook.register("hbm:late", log -> log.add("late"));
        hook.register(log -> log.add("default"));
        hook.register("hbm:early", log -> log.add("early"));
        assertEquals(List.of("early", "default", "late"), run(hook));
    }

    @Test
    void addonPhaseOrderedAgainstExisting() {
        Hook<Consumer<List<String>>> hook = recording();
        hook.register("ntmspace:atmosphere", log -> log.add("space"));
        hook.register(log -> log.add("default"));
        assertEquals(List.of("default", "space"), run(hook), "a phase added later runs after existing ones");
        hook.addPhaseOrdering("ntmspace:atmosphere", Hook.DEFAULT_PHASE);
        assertEquals(List.of("space", "default"), run(hook));
    }

    @Test
    void cyclicOrderingIsRejectedAndLeavesOrderIntact() {
        Hook<Consumer<List<String>>> hook = recording();
        hook.register("a:x", log -> log.add("x"));
        hook.register("a:y", log -> log.add("y"));
        hook.addPhaseOrdering("a:y", "a:x");
        assertThrows(IllegalArgumentException.class, () -> hook.addPhaseOrdering("a:x", "a:y"));
        assertEquals(List.of("y", "x"), run(hook));
    }

    @Test
    void verdictFirstNonPassWins() {
        Hook<Supplier<Verdict>> hook =
                Hook.of(listeners -> () -> Verdict.first(listeners, Supplier::get));
        assertEquals(Verdict.PASS, hook.invoker().get());
        hook.register(() -> Verdict.PASS);
        hook.register(() -> Verdict.DENY);
        hook.register(() -> Verdict.ALLOW);
        assertEquals(Verdict.DENY, hook.invoker().get());
        assertTrue(Verdict.PASS.orElse(true));
    }
}
