package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Tag files declared in code.
 *
 * <p>A tag file is the most paternal shape in a data pack: a {@code replace} flag that is false in
 * every one of them and a list of ids. There is nothing in it a program cannot write, and nothing
 * in it a person should have to. What is worth writing down is the <i>rule</i> that produced the
 * list — that a material comes in ingots, or that these four items are all tars — and a rule
 * belongs in code, where it can be applied to a catalogue rather than transcribed once per member.
 *
 * <p>Membership is spelled exactly as the file spells it, including a leading {@code #} for a tag
 * that includes another tag, because the difference is real: an id names one item and must exist,
 * while a {@code #} reference names a tag that may legitimately be empty on a given loader.
 *
 * <p>The path is the modern singular one ({@code data/<ns>/tags/item/<path>.json}). Targets that
 * want the pre-1.21 plural directories get them from the build's own legacy pass, which renames
 * what lands in the processed resources — generated and authored files alike, since by then they
 * are the same pile of files.
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
     * {@linkplain Tag#optional optional}, and a tag that is deliberately declared with no members.
     *
     * <p>An empty tag is not nothing. A repair ingredient or a block list that names a tag needs the tag
     * to exist on every loader even while the mod has nothing to put in it, and declaring it empty is
     * how that is said.
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
