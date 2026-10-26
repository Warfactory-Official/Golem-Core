package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

/**
 * Worldgen type registration keeps declared map codecs independent of the native codec shape.
 */
public final class Structures {

    private Structures() {}

    /** A structure type for {@code codec}, to be registered under {@code Registries.STRUCTURE_TYPE}. */
    public static <S extends Structure> StructureType<S> type(MapCodec<S> codec) {
        //? if >=1.21 {
        return () -> codec;
        //?} else {
        /*return codec::codec;
        *///?}
    }

    /** The processor codec uses the same map/plain split as structure codecs. */
    public static <P extends StructureProcessor> StructureProcessorType<P> processorType(MapCodec<P> codec) {
        //? if >=1.21 {
        return () -> codec;
        //?} else {
        /*return codec::codec;
        *///?}
    }

    /**
     * A structure placement type for {@code codec}, to be registered under {@code Registries.STRUCTURE_PLACEMENT}. Same
     * codec-shape split as {@link #type}.
     */
    public static <P extends net.minecraft.world.level.levelgen.structure.placement.StructurePlacement>
            net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<P> placementType(MapCodec<P> codec) {
        //? if >=1.21 {
        return () -> codec;
        //?} else {
        /*return codec::codec;
        *///?}
    }

    /**
     * The structure starts of the structures in {@code tag} that reach into chunk {@code pos}, read during feature
     * placement (vanilla's own {@code StructureManager.startsForStructure} over the generation region, which may read
     * the starts of chunks up to eight away). {@code level} must be the {@code WorldGenRegion} a feature is given;
     * anything else answers an empty list.
     */
    public static java.util.List<net.minecraft.world.level.levelgen.structure.StructureStart> startsIn(
            net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.ChunkPos pos,
            net.minecraft.tags.TagKey<Structure> tag) {
        if (!(level instanceof net.minecraft.server.level.WorldGenRegion region)) return java.util.List.of();
        net.minecraft.world.level.StructureManager manager = level.getLevel().structureManager().forWorldGenRegion(region);
        //? if >=26.1 {
        net.minecraft.core.Registry<Structure> registry = manager.registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        //?} else {
        /*net.minecraft.core.Registry<Structure> registry = manager.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        *///?}
        return manager.startsForStructure(pos, structure -> registry.wrapAsHolder(structure).is(tag));
    }
}
