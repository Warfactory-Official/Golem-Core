package dev.golem.core.registry;

/**
 * An entity-type tag a registration can join with {@code .tag(...)}; see {@link BlockTag} for the spelling.
 *
 * <p>Below 1.21 the build's legacy rename covers the plural {@code entity_types} folder.
 */
public record EntityTag(String namespace, String path) {

    /** {@code namespace:path}. */
    public static EntityTag of(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) throw new IllegalArgumentException("not a tag id: " + id);
        return new EntityTag(id.substring(0, colon), id.substring(colon + 1));
    }
}
