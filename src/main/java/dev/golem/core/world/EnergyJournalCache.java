package dev.golem.core.world;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Keeps one live loader journal for all views of an owner's staged transaction state. */
final class EnergyJournalCache<J> {
    private final Map<Object, WeakReference<J>> journals = new WeakHashMap<>();

    J get(EnergySource source, Supplier<J> factory) {
        var key = source.transactionKey();
        if (key == null) return factory.get();
        var reference = journals.get(key);
        var journal = reference == null ? null : reference.get();
        if (journal == null) journals.put(key, new WeakReference<>(journal = factory.get()));
        return journal;
    }
}
