package dev.golem.core.dejson;

/**
 * Thrown when a model declaration is malformed.
 *
 * <p>Declarations are checked when they are made, not when they bake. A bad declaration that
 * survives to bake time surfaces as an untextured cube or a silently missing model with no
 * indication of which block asked for it, so everything checkable from the declaration alone is
 * checked at the registration site and names the block, the slot and the value.
 */
public final class DejsonException extends RuntimeException {

    public DejsonException(String message) {
        super(message);
    }
}
