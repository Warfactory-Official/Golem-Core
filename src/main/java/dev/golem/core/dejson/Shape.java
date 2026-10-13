package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * The set of model files one declaration stands in for.
 *
 * <p>Most shapes are a single model; a stair is three ({@code <base>}, {@code <base>_inner},
 * {@code <base>_outer}) and a slab is three ({@code <base>}, {@code <base>_top}, {@code <base>_double}).
 * The map is keyed by full model id so the derived names live in one place and the {@link Dispatch}
 * that points at them cannot drift.
 */
@FunctionalInterface
public interface Shape {

    /**
     * @param base the block's own model id, {@code <namespace>:block/<path>}
     * @return every model this shape emits, keyed by full model id
     */
    Map<String, JsonObject> models(String base);
}
