package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The blockstate half of a declaration: which variant key selects which model.
 *
 * <p>Keys are vanilla's own {@code property=value,property=value} form and are matched the same
 * way: order-independent, and a property the key omits is a wildcard. A key mapping to more than
 * one placement is a random pool, written as a JSON array exactly as a hand-written file would.
 */
public interface Dispatch {

    /** Variant key to the placements it selects, in a stable order. */
    Map<String, List<Placement>> placements();

    /** The blockstate properties this dispatch names, for the coverage check. */
    Set<String> properties();

    default Map<String, List<Variant>> variants(String base) {
        Models.requireId(base, "block model");
        Map<String, List<Variant>> variants = new LinkedHashMap<>();
        placements().forEach((key, entries) -> {
            List<Variant> bound = new ArrayList<>(entries.size());
            for (Placement placement : entries) {
                bound.add(placement.bind(base));
            }
            variants.put(key, bound);
        });
        return variants;
    }

    /** The {@code blockstates/<path>.json} this dispatch stands in for. */
    default JsonObject toJson(String base) {
        JsonObject variants = new JsonObject();
        variants(base).forEach((key, entries) -> {
            if (entries.size() == 1) {
                variants.add(key, entries.get(0).toJson());
            } else {
                JsonArray pool = new JsonArray();
                for (Variant variant : entries) {
                    pool.add(variant.toJson());
                }
                variants.add(key, pool);
            }
        });
        JsonObject json = new JsonObject();
        json.add("variants", variants);
        return json;
    }

    /**
     * Whether some variant key selects this state, where a state is its property values by name.
     *
     * <p>Matching is vanilla's: a key names a subset of the properties and every property it does
     * name must match, so a key that omits {@code waterlogged} covers both of its values. Kept here
     * and kept free of Minecraft types so the same check runs in a plain unit test, in a GameTest
     * against the real registry, and in the delivery layer.
     */
    default boolean covers(Map<String, String> state) {
        for (String key : placements().keySet()) {
            if (matches(key, state)) {
                return true;
            }
        }
        return false;
    }

    /** The variant keys that no state of {@code states} selects; a declaration that drifted. */
    default Set<String> unusedKeys(Iterable<Map<String, String>> states) {
        Set<String> unused = new LinkedHashSet<>(placements().keySet());
        for (Map<String, String> state : states) {
            unused.removeIf(key -> matches(key, state));
        }
        return unused;
    }

    static boolean matches(String key, Map<String, String> state) {
        if (key.isEmpty()) {
            return true;
        }
        for (String term : key.split(",")) {
            int equals = term.indexOf('=');
            if (equals < 0) {
                throw new DejsonException("variant key term '" + term + "' has no '='");
            }
            String value = state.get(term.substring(0, equals));
            if (value == null || !value.equals(term.substring(equals + 1))) {
                return false;
            }
        }
        return true;
    }

    /** Every model id this dispatch points at, for the "declared but never emitted" check. */
    default Set<String> referencedModels(String base) {
        Set<String> models = new LinkedHashSet<>();
        variants(base).values().forEach(entries -> entries.forEach(variant -> models.add(variant.model())));
        return models;
    }
}
