package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tag files declared in code.
 *
 * <p>A tag file is a {@code replace} flag (always false) and a list of ids; the rule that produced the list
 * (a material comes in ingots) is written in code and applied to a catalogue.
 *
 * <p>Membership is spelled as the file spells it, including a leading {@code #} for an included tag: an id names
 * one entry and must exist, while a {@code #} reference may legitimately be empty on a given loader.
 *
 * <p>The path is the modern singular one ({@code data/<ns>/tags/item/<path>.json}); the build's legacy pass
 * renames it to the pre-1.21 plural directories for older targets.
 */
public final class Tags {

    private Tags() {}

    /** An item tag: {@code data/<namespace>/tags/item/<path>.json}. */
    public static JsonObject item(String namespace, String path, String... values) {
        return tag("item", namespace, path, values);
    }

    /** A block tag: {@code data/<namespace>/tags/block/<path>.json}. */
    public static JsonObject block(String namespace, String path, String... values) {
        return tag("block", namespace, path, values);
    }

    /** A fluid tag: {@code data/<namespace>/tags/fluid/<path>.json}. */
    public static JsonObject fluid(String namespace, String path, String... values) {
        return tag("fluid", namespace, path, values);
    }

    /** A damage-type tag: {@code data/<namespace>/tags/damage_type/<path>.json}. */
    public static JsonObject damageType(String namespace, String path, String... values) {
        return tag("damage_type", namespace, path, values);
    }

    /** An entity-type tag: {@code data/<namespace>/tags/entity_type/<path>.json}. */
    public static JsonObject entityType(String namespace, String path, String... values) {
        return tag("entity_type", namespace, path, values);
    }

    /**
     * A tag built member by member, for the two things the one-line forms refuse: members that are
     * {@linkplain Tag#optional optional}, and a tag declared with no members.
     *
     * <p>A repair ingredient or block list that names a tag needs the tag to exist on every loader even while it
     * has no members.
     *
     * @param registry the registry folder: {@code item}, {@code block}, {@code entity_type}, ...
     */
    public static Tag of(String registry, String namespace, String path) {
        if (registry == null || registry.isEmpty() || namespace == null || namespace.isEmpty()
                || path == null || path.isEmpty()) {
            throw new DejsonException("a tag needs a registry, a namespace and a path");
        }
        return new Tag(registry, namespace, path);
    }

    /** A tag under construction; see {@link #of}. */
    public static final class Tag {

        private final String registry;
        private final String namespace;
        private final String path;
        private final JsonArray members = new JsonArray();

        private Tag(String registry, String namespace, String path) {
            this.registry = registry;
            this.namespace = namespace;
            this.path = path;
        }

        /** Required members: an id that must exist, or a {@code #tag} reference. */
        public Tag values(String... values) {
            for (String value : values) {
                requireMember(value);
                members.add(value);
            }
            return this;
        }

        /** Members that are skipped rather than failing the tag when absent, e.g. another mod's block. */
        public Tag optional(String... values) {
            for (String value : values) {
                requireMember(value);
                JsonObject member = new JsonObject();
                member.addProperty("id", value);
                member.addProperty("required", false);
                members.add(member);
            }
            return this;
        }

        public JsonObject register() {
            JsonObject tag = new JsonObject();
            tag.addProperty("replace", false);
            tag.add("values", members.deepCopy());
            return Data.declare("data/" + namespace + "/tags/" + registry + "/" + path + ".json", tag);
        }

        private void requireMember(String value) {
            if (value == null || value.isEmpty()) {
                throw new DejsonException("tag " + namespace + ":" + path + " has an empty member");
            }
            Models.requireId(value.startsWith("#") ? value.substring(1) : value, "tag member");
        }
    }

    /** Members contributed one at a time, by tag file path; written out sorted (see {@link #member}). */
    private static final Map<String, List<String>> MEMBERS = new LinkedHashMap<>();

    /**
     * Adds one required member to a tag that many declarations contribute to, such as
     * {@code minecraft:mineable/pickaxe}: each block's registration states its own membership instead of
     * one list naming every block.
     *
     * <p>Contributions are merged when the files are written, appended in sorted order (class initialisation order
     * differs between the build and a running game) after whatever a whole-file declaration of the same tag
     * ({@link #block}, {@link #of}) lists, or emitted as a tag of their own when there is none. A member stated
     * twice fails, because one of the two statements is dead.
     *
     * @param registry the registry folder: {@code block}, {@code item}, {@code entity_type}, ...
     */
    public static void member(String registry, String namespace, String path, String value) {
        if (registry == null || registry.isEmpty() || namespace == null || namespace.isEmpty()
                || path == null || path.isEmpty()) {
            throw new DejsonException("a tag member needs a registry, a namespace and a path");
        }
        if (value == null || value.isEmpty()) {
            throw new DejsonException("tag " + namespace + ":" + path + " has an empty member");
        }
        Models.requireId(value.startsWith("#") ? value.substring(1) : value, "tag member");
        String file = "data/" + namespace + "/tags/" + registry + "/" + path + ".json";
        synchronized (MEMBERS) {
            List<String> members = MEMBERS.computeIfAbsent(file, ignored -> new ArrayList<>());
            if (members.contains(value)) {
                throw new DejsonException(value + " is stated twice as a member of " + file);
            }
            members.add(value);
        }
    }

    /**
     * Folds the accumulated {@link #member} contributions into {@code files} and forgets them, so a
     * later call folds only what was contributed since. Called by {@link Data#files()}.
     */
    static void mergeMembers(Map<String, JsonObject> files) {
        synchronized (MEMBERS) {
            for (Map.Entry<String, List<String>> entry : MEMBERS.entrySet()) {
                JsonObject tag = files.get(entry.getKey());
                if (tag == null) {
                    tag = new JsonObject();
                    tag.addProperty("replace", false);
                    tag.add("values", new JsonArray());
                    files.put(entry.getKey(), tag);
                }
                JsonArray values = tag.getAsJsonArray("values");
                // Sorted: members arrive in class-initialisation order, which differs between the build-time
                // generator and a running game, so insertion order would make the shipped file unreproducible.
                for (String value : entry.getValue().stream().sorted().toList()) {
                    for (var existing : values) {
                        String id = existing.isJsonObject() ? existing.getAsJsonObject().get("id").getAsString()
                                : existing.getAsString();
                        if (id.equals(value)) {
                            throw new DejsonException(value + " is a member of " + entry.getKey()
                                    + " both by its own declaration and by the tag's member list");
                        }
                    }
                    values.add(value);
                }
            }
            MEMBERS.clear();
        }
    }

    /** Drops every accumulated contribution. Tests only. */
    static void resetMembers() {
        synchronized (MEMBERS) {
            MEMBERS.clear();
        }
    }

    private static JsonObject tag(String registry, String namespace, String path, String... values) {
        if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
            throw new DejsonException("a tag needs a namespace and a path");
        }
        if (values == null || values.length == 0) {
            throw new DejsonException("tag " + namespace + ":" + path + " lists nothing; "
                    + "an empty tag says less than no tag at all");
        }
        JsonArray members = new JsonArray();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                throw new DejsonException("tag " + namespace + ":" + path + " has an empty member");
            }
            Models.requireId(value.startsWith("#") ? value.substring(1) : value, "tag member");
            members.add(value);
        }
        JsonObject tag = new JsonObject();
        tag.addProperty("replace", false);
        tag.add("values", members);
        return Data.declare("data/" + namespace + "/tags/" + registry + "/" + path + ".json", tag);
    }
}
