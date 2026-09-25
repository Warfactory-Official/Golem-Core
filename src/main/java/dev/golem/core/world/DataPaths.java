package dev.golem.core.world;

/**
 * Data-pack folder names for files written at runtime. 1.21 singularised them; the loaders' biome
 * modifiers live under their own namespace folder.
 */
public final class DataPaths {

    private DataPaths() {}

    /** {@code tags/item/<path>} ({@code tags/items} before 1.21). */
    public static String itemTag(String path) {
        //? if >=1.21 {
        return "tags/item/" + path;
        //?} else {
        /*return "tags/items/" + path;
        *///?}
    }

    /** {@code tags/block/<path>} ({@code tags/blocks} before 1.21). */
    public static String blockTag(String path) {
        //? if >=1.21 {
        return "tags/block/" + path;
        //?} else {
        /*return "tags/blocks/" + path;
        *///?}
    }

    public static String recipe(String path) {
        //? if >=1.21 {
        return "recipe/" + path;
        //?} else {
        /*return "recipes/" + path;
        *///?}
    }

    public static String blockLootTable(String path) {
        //? if >=1.21 {
        return "loot_table/blocks/" + path;
        //?} else {
        /*return "loot_tables/blocks/" + path;
        *///?}
    }

    /** The loader's biome-modifier folder, or null on Fabric (which has no data-driven modifiers). */
    public static String biomeModifier(String path) {
        //? if neoforge {
        return "neoforge/biome_modifier/" + path;
        //?} elif forge {
        /*return "forge/biome_modifier/" + path;
        *///?} else {
        /*return null;
        *///?}
    }
}
