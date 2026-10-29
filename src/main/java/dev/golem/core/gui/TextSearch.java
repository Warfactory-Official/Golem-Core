package dev.golem.core.gui;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Substring matching for a search bar: through Just Enough Characters' public {@code Match} when it is installed, so
 * pinyin finds Chinese names as in JEI, else {@link String#contains}. Callers fold case themselves.
 */
public final class TextSearch {
    private static final MethodHandle JECH_CONTAINS;

    static {
        MethodHandle contains = null;
        try {
            Class<?> match = Class.forName("me.towdium.jecharacters.utils.Match");
            contains = MethodHandles.publicLookup().findStatic(match, "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class));
        } catch (ReflectiveOperationException | LinkageError absent) {
            // Just Enough Characters is not installed
        }
        JECH_CONTAINS = contains;
    }

    private TextSearch() {}

    public static boolean contains(String text, String query) {
        if (JECH_CONTAINS == null || query.isEmpty()) return text.contains(query);
        try {
            return (boolean) JECH_CONTAINS.invoke(text, (CharSequence) query);
        } catch (Throwable failure) {
            // invoke declares Throwable; a JECh fault must not break the screen, so match plainly instead
            return text.contains(query);
        }
    }
}
