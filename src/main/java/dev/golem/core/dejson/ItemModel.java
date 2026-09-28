package dev.golem.core.dejson;

import com.google.gson.JsonObject;

/**
 * One node of a 26.1 item definition's model tree: a plain model reference, a {@code select} over a
 * property, a {@code condition}, a {@code special} renderer, or a type the consumer defines itself.
 *
 * <p>{@link Dejson.ItemDeclaration#definition} takes the root of the tree. Below 26.1 there is no
 * definition layer; {@code legacy-assets} derives the old {@code models/item/<path>.json} from the
 * emitted definition, so a tree declared here reaches every target through the same build step an
 * authored file did.
 *
 * @see ItemModels
 */
@FunctionalInterface
public interface ItemModel {

    JsonObject toJson();
}
