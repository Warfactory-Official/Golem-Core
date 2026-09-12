package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/**
 * One entry in an item definition's tint list. Entry <i>n</i> colours {@code layer<n>} of the model
 * the definition names, so the list is positional and its length is the model's layer count.
 *
 * <p>Tints live on the definition, which only 26.1 and later read. Below that the same colour comes
 * from a registered item-colour handler, so {@code legacy-assets} derives the old
 * {@code models/item/<path>.json} from the definition's model alone and drops the tints.
 */
@FunctionalInterface
public interface Tint {

    JsonObject toJson();
}
