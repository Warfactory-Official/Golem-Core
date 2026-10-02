package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The data-pack half of the same idea: files under {@code data/} declared in code rather than
 * written by hand.
 *
 * <p>{@link Dejson} covers the client's assets, where a declaration has to know what a model and a
 * blockstate are. A data file has no such vocabulary in common — a loot table and an item tag share
 * nothing but their encoding — so this holds the plain path-to-content registry and the shapes are
 * built on top of it by {@link Drops} and {@link Tags}.
 *
 * <p>The rules are the assets' rules. A path that already exists as a file is left to the file; the
 * declaration is checked against it while both are there, and only then is the file safe to delete.
 * Nothing here touches a Minecraft class, so the whole registry can be built and written out by a
 * plain JVM at build time.
 */
public final class Data {

    private static final Map<String, JsonObject> FILES = new ConcurrentHashMap<>();

    /**
     * Files whose content depends on other declarations (an advancement naming every recipe that makes
     * an item). They are built only when the whole set is asked for, after every declaring class ran.
     */
    private static final Map<String, Supplier<JsonObject>> LATER = Collections.synchronizedMap(new LinkedHashMap<>());

    private static volatile Path authored;

    private Data() {}

    /**
     * Publishes one data file.
     *
     * @param path the path under the resource root, e.g. {@code data/hbm/loot_table/blocks/foo.json}
     */
    public static JsonObject declare(String path, JsonObject content) {
        if (path == null || !path.startsWith("data/") || !path.endsWith(".json")) {
            throw new DejsonException("a data declaration needs a data/....json path, got " + path);
        }
        if (content == null) {
            throw new DejsonException(path + " was declared with no content");
        }
        if (FILES.putIfAbsent(path, content) != null) {
            throw new DejsonException(path + " is declared twice");
        }
        return content;
    }

    /**
     * Publishes one client asset that is not a model, blockstate or item definition: an equipment
     * asset, an atlas source list. Those three are derived from {@link Dejson}'s declarations; these
     * have no such vocabulary in common, so they are kept here by path like the data files.
     *
     * @param path the path under the resource root, e.g. {@code assets/hbm/equipment/steel.json}
     */
    public static JsonObject declareAsset(String path, JsonObject content) {
        if (path == null || !path.startsWith("assets/") || !path.endsWith(".json")) {
            throw new DejsonException("an asset declaration needs an assets/....json path, got " + path);
        }
        if (content == null) {
            throw new DejsonException(path + " was declared with no content");
        }
        if (FILES.putIfAbsent(path, content) != null) {
            throw new DejsonException(path + " is declared twice");
        }
        return content;
    }

    /**
     * Publishes one data file whose content is computed from the finished declaration set, e.g. one that
     * lists every declared or authored recipe producing an item. {@code content} runs once, the first time
     * {@link #files()} is called, which the build does only after every declaring class has run.
     */
    public static void declareLater(String path, Supplier<JsonObject> content) {
        if (path == null || !path.startsWith("data/") || !path.endsWith(".json")) {
            throw new DejsonException("a data declaration needs a data/....json path, got " + path);
        }
        if (content == null) {
            throw new DejsonException(path + " was declared with no content");
        }
        if (FILES.containsKey(path) || LATER.putIfAbsent(path, content) != null) {
            throw new DejsonException(path + " is declared twice");
        }
    }

    /** The hand-written resource root the build holds the declarations against, when one was given. */
    public static Path authoredRoot() {
        return authored;
    }

    /** Set by the build before any declaring class runs. */
    public static void authoredRoot(Path root) {
        authored = root;
    }

    /** Every data file the current declarations stand for, keyed by its path under the root. */
    public static Map<String, JsonObject> files() {
        synchronized (LATER) {
            if (!LATER.isEmpty()) {
                Map<String, Supplier<JsonObject>> pending = new LinkedHashMap<>(LATER);
                LATER.clear();
                pending.forEach((path, content) -> {
                    JsonObject json = content.get();
                    if (json == null) throw new DejsonException(path + " computed no content");
                    if (FILES.putIfAbsent(path, json) != null) throw new DejsonException(path + " is declared twice");
                });
            }
            // Tag members stated one per declaration (Tags.member) join their tag file last, so they can
            // extend a whole-file declaration made anywhere in the run.
            Tags.mergeMembers(FILES);
        }
        return Collections.unmodifiableMap(FILES);
    }

    public static JsonObject file(String path) {
        return files().get(path);
    }

    public static int size() {
        return FILES.size() + LATER.size();
    }

    /** Drops every declaration. Tests only. */
    public static void reset() {
        FILES.clear();
        LATER.clear();
        Tags.resetMembers();
    }
}
