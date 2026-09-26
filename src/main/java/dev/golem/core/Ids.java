package dev.golem.core;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import net.minecraft.resources.ResourceKey;

/**
 * Construction and access for namespaced ids.
 *
 * <p>26.1 renamed {@code ResourceLocation} to {@code Identifier} and {@code ResourceKey#location()} to
 * {@code identifier()}. The rename itself is handled by a Stonecutter string replacement across the
 * shared source, so consumers keep writing {@code Identifier}; what a replacement cannot fix is the
 * <i>construction</i> call, which also differs by version:
 *
 * <ul>
 *   <li>1.20.1: {@code new ResourceLocation(ns, path)} — the constructor is public.</li>
 *   <li>1.21.1: the constructor is private; {@code ResourceLocation.fromNamespaceAndPath(ns, path)}.</li>
 *   <li>26.1: {@code Identifier.fromNamespaceAndPath(ns, path)}.</li>
 * </ul>
 *
 * <p>Routing every construction through {@link #of} means the mass rename stays a pure text
 * substitution and this is the single place that knows about the three spellings.
 */
public final class Ids {

    private Ids() {}

    /** Normalizes historical vanilla item registry names to the newest API's spelling. */
    public static String canonicalItemId(String id) {
        return switch (id) {
            case "minecraft:chain" -> "minecraft:iron_chain";
            case "minecraft:grass" -> "minecraft:short_grass";
            case "minecraft:scute" -> "minecraft:turtle_scute";
            default -> id;
        };
    }

    /** The id type for the target version: {@code Identifier} on 26.1+, {@code ResourceLocation} below. */
    public static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            of(String namespace, String path) {
        //? if >=26.1 {
        return Identifier.fromNamespaceAndPath(namespace, path);
        //?} elif >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    /** Parses a complete namespaced id using the native parser for the target version. */
    public static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            parse(String id) {
        //? if >=26.1 {
        return Identifier.parse(id);
        //?} elif >=1.21 {
        /*return ResourceLocation.parse(id);
        *///?} else {
        /*return new ResourceLocation(id);
        *///?}
    }

    /** {@link #parse}, or null for a malformed id (saved data from another mod set). */
    public static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            tryParse(String id) {
        //? if >=26.1 {
        return Identifier.tryParse(id);
        //?} else {
        /*return ResourceLocation.tryParse(id);
        *///?}
    }

    /** Creates an id in Minecraft's namespace. */
    public static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            minecraft(String path) {
        return of("minecraft", path);
    }

    /** The id a {@link ResourceKey} points at. Spelled {@code identifier()} on 26.1, {@code location()} below. */
    public static
            //? if >=26.1 {
            Identifier
            //?} else {
            /*ResourceLocation
            *///?}
            keyId(ResourceKey<?> key) {
        //? if >=26.1 {
        return key.identifier();
        //?} else {
        /*return key.location();
        *///?}
    }
}
