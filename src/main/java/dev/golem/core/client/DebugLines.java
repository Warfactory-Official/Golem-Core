package dev.golem.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Extra lines on the F3 debug screen, registered without naming a loader's hook.
 *
 * <p>26.1 turned the debug screen into a registry of {@code DebugScreenEntry}, but the registration
 * method is private and each loader reopens it differently: NeoForge fires a mod-bus event that also
 * carries the profile the entry belongs to, and Fabric ships no equivalent at all, so there the lines
 * are appended by a mixin. Both spellings live here.
 *
 * <p>A no-op below 26.1, where the debug screen is a pair of hand-built string lists rather than a
 * registry. Nothing is lost by that today: the only consumer is the instancing engine's own census,
 * and the engine does not exist there either.
 *
 * <p>Client-only.
 */
public final class DebugLines {

    /** Writes this source's current lines, one call per line, in the order they should appear. */
    @FunctionalInterface
    public interface Source {
        void lines(Consumer<String> out);
    }

    private record Entry(String namespace, String path, Source source) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean hooked;

    private DebugLines() {}

    /**
     * Adds a source of debug-screen lines. Safe to call on any node; where the loader has no hook this
     * records the source and nothing reads it.
     */
    public static synchronized void register(String namespace, String path, Source source) {
        ENTRIES.add(new Entry(namespace, path, source));
        //? if neoforge && >=26.1 {
        if (hooked) return;
        hooked = true;
        dev.golem.core.platform.NeoForgeEntrypoint.modBus().addListener(
                (net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent event) -> {
                    for (Entry entry : snapshot()) {
                        net.minecraft.resources.Identifier id =
                                dev.golem.core.Ids.of(entry.namespace(), entry.path());
                        event.register(id, (displayer, level, clientChunk, serverChunk) ->
                                entry.source().lines(displayer::addLine));
                        event.includeInProfile(id,
                                net.minecraft.client.gui.components.debug.DebugScreenProfile.DEFAULT,
                                net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.IN_OVERLAY);
                    }
                });
        //?}
    }

    private static synchronized List<Entry> snapshot() {
        return List.copyOf(ENTRIES);
    }

    /**
     * Writes every registered source's lines. Called by the Fabric mixin, which has to collect them by
     * hand because there is no registry to declare into.
     */
    public static void collectInto(Consumer<String> out) {
        for (Entry entry : snapshot()) entry.source().lines(out);
    }
}
