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
     * Whether a vanilla item tag exists on older versions but not in the newest API. Such a tag can hold a mod's
     * items on an old node through vanilla's own nesting, yet no id roster generated against the newest version
     * can name it. {@code minecraft:tools} is 1.19.4–1.20.4's aggregate of the per-tool tags; 26.1 ships only
     * the per-tool tags ({@code swords}, {@code axes}, ...). {@code minecraft:enchantable/sword} is 1.21.0–1.21.1's
     * Sweeping Edge gate ({@code ["#minecraft:swords"]}); 26.1 split it into {@code enchantable/sweeping} and
     * friends, so a sword's membership there is correct on 1.21.1 yet unnameable from a 26.1 roster.
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
