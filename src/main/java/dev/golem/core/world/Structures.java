package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Worldgen structure registration across the supported versions.
 *
 * <p>A {@link StructureType} is only a holder of its structure's codec, but the codec's shape changed:
 * 1.20.5 made {@code StructureType.codec()} a {@link MapCodec}, before that it was a plain {@code Codec}.
 * A mod declares its structure codec once as a {@code MapCodec} (what {@code RecordCodecBuilder.mapCodec}
 * and {@code Structure.simpleCodec} produce on every version) and gets the type from here.
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
}
