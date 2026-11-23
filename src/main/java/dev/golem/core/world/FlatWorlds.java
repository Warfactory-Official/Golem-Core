package dev.golem.core.world;

import dev.golem.core.Ids;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * A flat world places only its preset's structure sets; a mod whose structures belong in every world type names
 * its namespace here.
 */
public final class FlatWorlds {

    private static final Set<String> NAMESPACES = ConcurrentHashMap.newKeySet();

    private FlatWorlds() {}

    public static void placeStructures(String namespace) {
        NAMESPACES.add(namespace);
    }

    /** The preset's sets plus every set of a named namespace the preset left out. */
    public static Stream<Holder<StructureSet>> withModSets(Stream<Holder<StructureSet>> preset,
                                                           HolderLookup<StructureSet> lookup) {
        if (NAMESPACES.isEmpty()) return preset;
        List<Holder<StructureSet>> sets = new ArrayList<>(preset.toList());
        lookup.listElements()
                .filter(set -> NAMESPACES.contains(Ids.keyId(set.key()).getNamespace()))
                .filter(set -> sets.stream().noneMatch(listed -> listed.is(set.key())))
                .forEach(sets::add);
        return sets.stream();
    }
}
