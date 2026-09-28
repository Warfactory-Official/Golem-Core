package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Advancements declared in code, written in the newest version's JSON shape.
 *
 * <p>An advancement file is display data plus criteria, and both halves drift between versions in ways
 * the build layer already knows how to take back down: the plural {@code advancements} directory and
 * the {@code item} icon key on 1.20.1, and the full texture path for {@code background} before 26.1
 * (26.1 reads a client-asset id, {@code minecraft:gui/advancements/backgrounds/stone}, and adds the
 * {@code textures/} prefix and {@code .png} itself). Declaring the 26.1 shape once keeps every node's
 * copy in step.
 *
 * <pre>{@code
 * Advancements.advancement("hbm", "burner_press")
 *         .parent("hbm:root")
 *         .icon("hbm:machine_press")
 *         .title("achievement.burnerPress").description("achievement.burnerPress.desc")
 *         .criterion("produced", Advancements.trigger("hbm:item_produced").item("hbm:machine_press"))
 *         .craftedFrom("hbm:machine_press")
 *         .register();
 * }</pre>
 *
 * <p>Every criterion added to one advancement is an alternative: completing any of them completes it,
 * which is how a 1.7 achievement with several award routes behaved.
 */
public final class Advancements {

    public static final String TASK = "task";
    public static final String GOAL = "goal";
    public static final String CHALLENGE = "challenge";

    private static final List<String> REGISTERED = new ArrayList<>();

    private Advancements() {}

    /** Begins the advancement {@code namespace:path}. */
    public static Builder advancement(String namespace, String path) {
        return new Builder(namespace, path);
    }

    /** Every advancement id declared so far, in declaration order. */
    public static List<String> registered() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    /** A criterion of trigger {@code trigger} with no conditions yet. */
    public static Criterion trigger(String trigger) {
        return new Criterion(trigger);
    }

    /** Vanilla's never-firing criterion: the advancement can only be granted by command. */
    public static Criterion impossible() {
        return new Criterion("minecraft:impossible");
    }

    /** Vanilla's {@code recipe_crafted}: the player took the result of recipe {@code recipe}. */
    public static Criterion recipeCrafted(String recipe) {
        Models.requireId(recipe, "recipe");
        return new Criterion("minecraft:recipe_crafted").condition("recipe_id", recipe);
    }

    /** Vanilla's per-tick check, for conditions that are a state of the player rather than an event. */
    public static Criterion tick() {
        return new Criterion("minecraft:tick");
    }

    /** One item predicate matching exactly {@code items}, in the list form every version reads. */
    public static JsonObject items(String... items) {
        JsonArray list = new JsonArray();
        for (String item : items) {
            Models.requireId(item, "item");
            list.add(item);
        }
        JsonObject predicate = new JsonObject();
        predicate.add("items", list);
        return predicate;
    }

    /**
     * A player predicate on worn or held equipment; {@code slots} alternates slot name and item id:
     * {@code equipment("chest", "hbm:jackt", "mainhand", "hbm:shimmer_sledge")}.
     */
    public static JsonObject equipment(String... slots) {
        if (slots.length == 0 || slots.length % 2 != 0) {
            throw new DejsonException("equipment() takes slot/item pairs");
        }
        JsonObject equipment = new JsonObject();
        for (int i = 0; i < slots.length; i += 2) equipment.add(slots[i], items(slots[i + 1]));
        JsonObject player = new JsonObject();
        player.add("equipment", equipment);
        return player;
    }

    /**
     * A player predicate on one statistic reaching {@code min}: {@code stat("minecraft:picked_up",
     * "minecraft:slime_ball", 1)}. Vanilla's own per-tick check compares it, so an event that only a
     * statistic records needs no hook of its own.
     */
    public static JsonObject stat(String type, String stat, int min) {
        Models.requireId(type, "statistic type");
        Models.requireId(stat, "statistic");
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        entry.addProperty("stat", stat);
        JsonObject value = new JsonObject();
        value.addProperty("min", min);
        entry.add("value", value);
        JsonArray stats = new JsonArray();
        stats.add(entry);
        JsonObject specific = new JsonObject();
        specific.addProperty("type", "minecraft:player");
        specific.add("stats", stats);
        JsonObject player = new JsonObject();
        player.add("type_specific", specific);
        return player;
    }

    /** One criterion: a trigger id and its conditions. */
    public static final class Criterion {
        private final String trigger;
        private final JsonObject conditions = new JsonObject();

        private Criterion(String trigger) {
            Models.requireId(trigger, "trigger");
            this.trigger = trigger;
        }

        public Criterion condition(String key, String value) {
            conditions.addProperty(key, value);
            return this;
        }

        public Criterion condition(String key, JsonElement value) {
            conditions.add(key, value);
            return this;
        }

        /** The player predicate; for a custom {@link dev.golem.core.advancement.Trigger} and vanilla alike. */
        public Criterion player(JsonObject predicate) {
            return condition("player", predicate);
        }

        /** The kinds a {@code Trigger} event must be one of. */
        public Criterion kinds(String... kinds) {
            JsonArray list = new JsonArray();
            for (String kind : kinds) list.add(kind);
            return condition("kinds", list);
        }

        /** The item a {@code Trigger} event's stack must be. */
        public Criterion item(String... items) {
            return condition("item", items(items));
        }

        /** The lower bound a {@code Trigger} event's value must reach. */
        public Criterion atLeast(double min) {
            JsonObject bounds = new JsonObject();
            bounds.addProperty("min", min);
            return condition("value", bounds);
        }

        JsonObject json() {
            JsonObject json = new JsonObject();
            json.addProperty("trigger", trigger);
            if (conditions.size() > 0) json.add("conditions", conditions.deepCopy());
            return json;
        }
    }

    public static final class Builder {
        private final String namespace;
        private final String path;
        private final Map<String, Criterion> criteria = new LinkedHashMap<>();
        private final List<String> craftedFrom = new ArrayList<>();
        private String parent;
        private String icon;
        private JsonObject iconComponents;
        private String title;
        private String description;
        private String background;
        private String frame = TASK;
        private boolean toast = true;
        private boolean announce = true;
        private boolean hidden;
        private int experience;

        private Builder(String namespace, String path) {
            if (namespace == null || namespace.isEmpty() || path == null || path.isEmpty()) {
                throw new DejsonException("an advancement needs a namespace and a path");
            }
            this.namespace = namespace;
            this.path = path;
        }

        public Builder parent(String parent) {
            Models.requireId(parent, "parent advancement");
            this.parent = parent;
            return this;
        }

        public Builder icon(String item) {
            Models.requireId(item, "icon item");
            this.icon = item;
            return this;
        }

        /** The icon's data components, in the 26.1 spelling; the legacy build layer rewrites them. */
        public Builder iconComponents(JsonObject components) {
            this.iconComponents = components;
            return this;
        }

        /** Translation keys of the title and description. */
        public Builder title(String key) {
            this.title = key;
            return this;
        }

        public Builder description(String key) {
            this.description = key;
            return this;
        }

        /** A tab root's background, as a 26.1 client asset id: {@code minecraft:gui/advancements/backgrounds/stone}. */
        public Builder background(String asset) {
            Models.requireId(asset, "background");
            if (asset.endsWith(".png") || asset.contains(":textures/")) {
                throw new DejsonException(asset + ": write the background as a client asset id, not a texture path");
            }
            this.background = asset;
            return this;
        }

        public Builder frame(String frame) {
            if (!TASK.equals(frame) && !GOAL.equals(frame) && !CHALLENGE.equals(frame)) {
                throw new DejsonException("'" + frame + "' is not an advancement frame");
            }
            this.frame = frame;
            return this;
        }

        public Builder toast(boolean toast) {
            this.toast = toast;
            return this;
        }

        public Builder announce(boolean announce) {
            this.announce = announce;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder experience(int experience) {
            this.experience = experience;
            return this;
        }

        public Builder criterion(String name, Criterion criterion) {
            if (criteria.putIfAbsent(name, criterion) != null) {
                throw new DejsonException(namespace + ":" + path + " names criterion " + name + " twice");
            }
            return this;
        }

        /**
         * Adds a {@code recipe_crafted} alternative for every recipe, declared or authored, whose result is
         * {@code item}. Resolved once every declaration exists, so a recipe added later is picked up without
         * touching the advancement.
         */
        public Builder craftedFrom(String... items) {
            for (String item : items) {
                Models.requireId(item, "crafted item");
                craftedFrom.add(item);
            }
            return this;
        }

        public void register() {
            if (icon == null || title == null || description == null) {
                throw new DejsonException(namespace + ":" + path + " needs an icon, a title and a description");
            }
            if (criteria.isEmpty() && craftedFrom.isEmpty()) {
                throw new DejsonException(namespace + ":" + path + " has no criterion; use impossible() for a"
                        + " command-only advancement");
            }
            String file = "data/" + namespace + "/advancement/" + path + ".json";
            synchronized (REGISTERED) {
                REGISTERED.add(namespace + ":" + path);
            }
            if (craftedFrom.isEmpty()) {
                Data.declare(file, json(criteria));
            } else {
                Map<String, Criterion> fixed = new LinkedHashMap<>(criteria);
                List<String> items = List.copyOf(craftedFrom);
                Data.declareLater(file, () -> {
                    Map<String, Criterion> all = new LinkedHashMap<>(fixed);
                    for (String recipe : recipesProducing(items)) {
                        all.putIfAbsent("crafted_" + recipe.replace(':', '_').replace('/', '_'), recipeCrafted(recipe));
                    }
                    if (all.isEmpty()) {
                        throw new DejsonException(namespace + ":" + path + " found no recipe producing " + items);
                    }
                    return json(all);
                });
            }
        }

        private JsonObject json(Map<String, Criterion> criteria) {
            JsonObject json = new JsonObject();
            if (parent != null) json.addProperty("parent", parent);

            JsonObject display = new JsonObject();
            JsonObject iconJson = new JsonObject();
            iconJson.addProperty("id", icon);
            if (iconComponents != null) iconJson.add("components", iconComponents.deepCopy());
            display.add("icon", iconJson);
            display.add("title", translate(title));
            display.add("description", translate(description));
            if (background != null) display.addProperty("background", background);
            display.addProperty("frame", frame);
            display.addProperty("show_toast", toast);
            display.addProperty("announce_to_chat", announce);
            display.addProperty("hidden", hidden);
            json.add("display", display);

            JsonObject criteriaJson = new JsonObject();
            JsonArray any = new JsonArray();
            criteria.forEach((name, criterion) -> {
                criteriaJson.add(name, criterion.json());
                any.add(name);
            });
            json.add("criteria", criteriaJson);
            JsonArray requirements = new JsonArray();
            requirements.add(any);
            json.add("requirements", requirements);

            if (experience > 0) {
                JsonObject rewards = new JsonObject();
                rewards.addProperty("experience", experience);
                json.add("rewards", rewards);
            }
            return json;
        }
    }

    private static JsonObject translate(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }

    /**
     * Ids of every recipe whose result is one of {@code items}: the declared ones, then the authored
     * {@code data/<ns>/recipe} tree. Sorted, so the file is stable between builds.
     */
    static List<String> recipesProducing(List<String> items) {
        TreeSet<String> found = new TreeSet<>();
        for (Map.Entry<String, JsonObject> file : Data.files().entrySet()) {
            String id = recipeId(file.getKey());
            String result = id == null ? null : resultOf(file.getValue());
            if (result != null && items.contains(result)) found.add(id);
        }
        Path root = Data.authoredRoot();
        Path data = root == null ? null : root.resolve("data");
        if (data != null && Files.isDirectory(data)) {
            try (Stream<Path> paths = Files.walk(data)) {
                paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    String relative = "data/" + data.relativize(p).toString().replace('\\', '/');
                    String id = recipeId(relative);
                    if (id == null) return;
                    try {
                        JsonElement json = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                        String result = json.isJsonObject() ? resultOf(json.getAsJsonObject()) : null;
                        if (result != null && items.contains(result)) found.add(id);
                    } catch (IOException failure) {
                        throw new UncheckedIOException("could not read " + p, failure);
                    } catch (RuntimeException malformed) {
                        // Not a recipe this can read; the game will say so on its own.
                    }
                });
            } catch (IOException failure) {
                throw new UncheckedIOException("could not walk " + data, failure);
            }
        }
        return List.copyOf(found);
    }

    /** {@code data/hbm/recipe/foo/bar.json} is recipe {@code hbm:foo/bar}; anything else is not a recipe. */
    private static String recipeId(String path) {
        String[] parts = path.split("/", 4);
        if (parts.length < 4 || !"data".equals(parts[0]) || !"recipe".equals(parts[2]) || !parts[3].endsWith(".json")) {
            return null;
        }
        return parts[1] + ":" + parts[3].substring(0, parts[3].length() - 5);
    }

    private static String resultOf(JsonObject recipe) {
        JsonElement result = recipe.get("result");
        if (result == null) return null;
        if (result.isJsonPrimitive()) return result.getAsString();
        if (result.isJsonObject()) {
            JsonObject object = result.getAsJsonObject();
            if (object.has("id")) return object.get("id").getAsString();
            if (object.has("item")) return object.get("item").getAsString();
        }
        return null;
    }
}
