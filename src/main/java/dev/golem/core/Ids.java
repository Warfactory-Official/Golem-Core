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
 * <p>A Stonecutter string replacement maps the 26.1 name {@code Identifier} to {@code ResourceLocation}, but it
 * cannot fix the <i>construction</i> call, which differs by version:
 *
 * <ul>
 *   <li>1.20.1: {@code new ResourceLocation(ns, path)}. The constructor is public.</li>
 *   <li>1.21.1: the constructor is private; {@code ResourceLocation.fromNamespaceAndPath(ns, path)}.</li>
 *   <li>26.1: {@code Identifier.fromNamespaceAndPath(ns, path)}.</li>
 * </ul>
 *
 * <p>Every construction goes through {@link #of}, the only place that knows the three spellings.
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

    /**
     * The registry name this node gives a vanilla item that the newest API spells {@code id}: the inverse of
     * {@link #canonicalItemId}. {@code "minecraft:short_grass"} is {@code "minecraft:grass"} on 1.20.1; an id the node
     * registers as written (every mod id, every unrenamed vanilla id) comes back unchanged, as does one no spelling of
     * which is registered.
     */
    public static String nodeItemId(String id) {
        if (!id.startsWith("minecraft:") || net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(parse(id))) return id;
        for (String old : new String[] {"minecraft:chain", "minecraft:grass", "minecraft:scute"}) {
            if (canonicalItemId(old).equals(id) && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(parse(old))) return old;
        }
        return id;
    }

    /** As {@link #nodeItemId}, for the blocks of those renamed items ({@code short_grass} and {@code iron_chain} are blocks too). */
    public static String nodeBlockId(String id) {
        if (!id.startsWith("minecraft:") || net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(parse(id))) return id;
        for (String old : new String[] {"minecraft:chain", "minecraft:grass"}) {
            if (canonicalItemId(old).equals(id) && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(parse(old))) return old;
        }
        return id;
    }

    /**
     * Whether a vanilla item tag exists on older versions but not in the newest API, so no id roster generated
     * against the newest version can name it. {@code minecraft:tools} (1.19.4–1.20.4) aggregates the per-tool tags;
     * {@code minecraft:enchantable/sword} (1.21.0–1.21.1) is the Sweeping Edge gate, which 26.1 split into
     * {@code enchantable/sweeping} and others.
     */
    public static boolean isRetiredVanillaItemTag(String id) {
        return switch (id) {
            case "minecraft:tools", "minecraft:enchantable/sword" -> true;
            default -> false;
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
