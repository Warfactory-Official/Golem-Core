package dev.golem.core.dejson;

import com.google.gson.JsonObject;

import java.util.Map;

/**
 * The set of model files one declaration stands in for.
 *
 * <p>Most shapes are a single model, but the irregular ones are not: a stair is three
 * ({@code <base>}, {@code <base>_inner}, {@code <base>_outer}) and a slab is three
 * ({@code <base>}, {@code <base>_top}, {@code <base>_double}). Returning a map keyed by full model
 * id keeps that arity out of the caller's hands, and keeps the derived names in one place so the
 * {@link Dispatch} that points at them cannot drift.
 */
@FunctionalInterface
public interface Shape {

    /**
     * @param base the block's own model id, {@code <namespace>:block/<path>}
     * @return every model this shape emits, keyed by full model id
     */
    Map<String, JsonObject> models(String base);
}
