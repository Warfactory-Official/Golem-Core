package dev.golem.core.registry;

import dev.golem.core.GolemCore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Replaces another mod's registry entry under its own id, before the registries are filled: the way an addon
 * rewrites what an existing block, item or block entity type IS while worlds, recipes, tags and the owning mod's own
 * references keep working.
 *
 * <pre>{@code
 * // in the addon's ModEntry.substitute():
 * Substitutions.block("ntmspace", "hbm:machine_chungus", SpaceChungus::new);   // HBM's block properties, new class
 * Substitutions.entry("ntmspace", "block_entity_type", "hbm:machine_chungus",
 *         key -> BlockEntities.type(SpaceChungusEntity::new, ModBlocks.MACHINE_CHUNGUS));
 * }</pre>
 *
 * <p>A substitute must be a subclass of the class it replaces: the owning mod's fields are typed by that class.
 * One id may be substituted by one mod only; a second substitution fails at once, naming both mods, rather than one
 * addon silently overwriting another. A substitution that matches no registration is reported when the registries
 * are filled, and every applied one is logged.
 *
 * <p>Declare substitutions in {@link dev.golem.core.platform.ModEntry#substitute()}: on Fabric that runs before any
 * mod's declarations, and on NeoForge and Forge before any registry is filled.
 */
public final class Substitutions {

    private record Substitution(String owner, Function<Object, ?> factory) {}

    // kind (BLOCK, ITEM, or ENTRY + registry path) -> id -> substitution; guarded by itself (NeoForge/Forge may
    // construct mods in parallel)
    private static final Map<String, Map<String, Substitution>> BY_REGISTRY = new LinkedHashMap<>();
    private static final List<String> APPLIED = new ArrayList<>();

    private Substitutions() {}

    /** {@code id}'s block is built by {@code factory}, given the block properties its own registration states. */
    @SuppressWarnings("unchecked")
    public static void block(String owner, String id, Function<BlockBehaviour.Properties, ? extends Block> factory) {
        put(BLOCK, owner, id, (Function<Object, ?>) (Function<?, ?>) factory);
    }

    /** {@code id}'s item is built by {@code factory}, given the item properties its own registration states. */
    @SuppressWarnings("unchecked")
    public static void item(String owner, String id, Function<Item.Properties, ? extends Item> factory) {
        put(ITEM, owner, id, (Function<Object, ?>) (Function<?, ?>) factory);
    }

    /**
     * Any other registry's entry ({@code registry} is its path, e.g. {@code "block_entity_type"}, {@code "menu"}):
     * {@code factory} receives the entry's {@code ResourceKey} and replaces the registration's whole factory.
     */
    @SuppressWarnings("unchecked")
    public static <T> void entry(String owner, String registry, String id,
                                 Function<ResourceKey<T>, ? extends T> factory) {
        put(ENTRY + registry, owner, id, (Function<Object, ?>) (Function<?, ?>) factory);
    }

    // Kinds: the properties forms are resolved by DeferredRegister.Blocks/Items as they build an entry from its
    // properties, the whole-factory form by DeferredRegister.flushTo for any registry.
    static final String BLOCK = "block";
    static final String ITEM = "item";
    static final String ENTRY = "entry ";

    private static void put(String registry, String owner, String id, Function<Object, ?> factory) {
        synchronized (BY_REGISTRY) {
            Substitution previous = BY_REGISTRY.computeIfAbsent(registry, r -> new LinkedHashMap<>())
                    .putIfAbsent(id, new Substitution(owner, factory));
            if (previous != null) {
                throw new IllegalStateException(registry + " " + id + " is substituted by both " + previous.owner()
                        + " and " + owner + "; only one mod may replace an entry");
            }
        }
    }

    /**
     * The factory to use for {@code id} in {@code registry}: the substitute if one was declared, else
     * {@code original}. Called by {@link DeferredRegister} as each entry is built, so every substitution declared
     * before the registries are filled is seen.
     */
    @SuppressWarnings("unchecked")
    static <P, R> Function<P, ? extends R> resolve(String registry, String id, Function<P, ? extends R> original) {
        Substitution substitution;
        synchronized (BY_REGISTRY) {
            Map<String, Substitution> entries = BY_REGISTRY.get(registry);
            substitution = entries == null ? null : entries.get(id);
            if (substitution == null) return original;
            APPLIED.add(registry + " " + id);
        }
        GolemCore.LOG.info("Substitution: {} {} is built by {}", registry, id, substitution.owner());
        return (Function<P, ? extends R>) (Function<?, ?>) substitution.factory();
    }

    /** Reports substitutions that matched no registration; Golem Core calls it once the registries are filled. */
    public static void reportUnmatched() {
        synchronized (BY_REGISTRY) {
            BY_REGISTRY.forEach((registry, entries) -> entries.forEach((id, substitution) -> {
                if (!APPLIED.contains(registry + " " + id)) {
                    GolemCore.LOG.error("Substitution of {} {} by {} matched no registration", registry, id,
                            substitution.owner());
                }
            }));
        }
    }
}
