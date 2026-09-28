package dev.golem.core.dejson;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The condition of one {@link States#multipart} case: property values that must all hold, or a list
 * of such conditions of which any may.
 *
 * <p>A value is vanilla's own spelling, so {@code "north|south"} matches either.
 */
public final class When {

    private final Map<String, String> all;
    private final List<When> any;

    private When(Map<String, String> all, List<When> any) {
        this.all = all;
        this.any = any;
    }

    /** {@code property=value} pairs, all of which must hold. */
    public static When of(String... propertyThenValue) {
        if (propertyThenValue.length == 0 || propertyThenValue.length % 2 != 0) {
            throw new DejsonException("When.of takes property/value pairs, got " + propertyThenValue.length
                    + " argument(s)");
        }
        Map<String, String> terms = new LinkedHashMap<>();
        for (int index = 0; index < propertyThenValue.length; index += 2) {
            if (terms.putIfAbsent(propertyThenValue[index], propertyThenValue[index + 1]) != null) {
                throw new DejsonException("When names '" + propertyThenValue[index] + "' twice");
            }
        }
        return new When(terms, null);
    }

    /** Any of the given conditions: vanilla's {@code OR}. */
    public static When or(When... conditions) {
        if (conditions.length < 2) {
            throw new DejsonException("When.or needs at least two conditions");
        }
        return new When(null, List.of(conditions));
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (all != null) {
            all.forEach(json::addProperty);
        } else {
            JsonArray list = new JsonArray();
            any.forEach(condition -> list.add(condition.toJson()));
            json.add("OR", list);
        }
        return json;
    }
}
