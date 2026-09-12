package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Every data file the current declarations stand for, keyed by its path under the root. */
    public static Map<String, JsonObject> files() {
        return Collections.unmodifiableMap(FILES);
    }

    public static JsonObject file(String path) {
        return FILES.get(path);
    }

    public static int size() {
        return FILES.size();
    }

    /** Drops every declaration. Tests only. */
    public static void reset() {
        FILES.clear();
    }
}
