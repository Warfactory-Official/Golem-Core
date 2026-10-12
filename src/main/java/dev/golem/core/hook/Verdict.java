package dev.golem.core.hook;

import java.util.List;
import java.util.function.Function;

/**
 * A listener's answer to "may this happen?": {@link #ALLOW} or {@link #DENY} decide, {@link #PASS} leaves it to the
 * next listener and finally to the default.
 */
public enum Verdict {
    ALLOW, DENY, PASS;

    /**
     * The first listener that does not {@link #PASS} decides; if all pass, the answer is {@link #PASS}. {@code ask}
     * asks one listener. Use as a hook's combiner body:
     * {@code Hook.of(listeners -> (level, pos) -> Verdict.first(listeners, l -> l.mayBurn(level, pos)))}.
     */
    public static <T> Verdict first(List<T> listeners, Function<T, Verdict> ask) {
        for (T listener : listeners) {
            Verdict verdict = ask.apply(listener);
            if (verdict != null && verdict != PASS) return verdict;
        }
        return PASS;
    }

    /** {@code true} for {@link #ALLOW}, {@code false} for {@link #DENY}, {@code fallback} for {@link #PASS}. */
    public boolean orElse(boolean fallback) {
        return this == PASS ? fallback : this == ALLOW;
    }
}
