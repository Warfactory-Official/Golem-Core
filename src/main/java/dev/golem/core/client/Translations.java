package dev.golem.core.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Shows {@code replacement}'s text for {@code key} wherever the loaded language defines it: renaming another mod's key
 * without writing it again, which has no fixed winner in pack order. Client-only.
 */
public final class Translations {

    private static final Map<String, String> REDIRECTS = new ConcurrentHashMap<>();
    private static boolean installed;

    private Translations() {}

    public static synchronized void redirect(String key, String replacement) {
        REDIRECTS.put(key, replacement);
        if (installed) return;
        installed = true;
        // Every resource reload injects a fresh language, so re-wrap whichever one is current.
        ClientTicks.onEndTick(() -> {
            Language current = Language.getInstance();
            if (!(current instanceof Redirecting)) Language.inject(new Redirecting(current));
        });
    }

    private static final class Redirecting extends Language {
        private final Language base;

        Redirecting(Language base) {
            this.base = base;
        }

        private String resolve(String key) {
            String replacement = REDIRECTS.get(key);
            return replacement != null && base.has(replacement) ? replacement : key;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return base.getOrDefault(resolve(key), fallback);
        }

        @Override
        public boolean has(String key) {
            return base.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return base.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return base.getVisualOrder(text);
        }
    }
}
