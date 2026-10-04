package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement-modifier type registration across the supported versions.
 *
 * <p>Like {@link Structures}: a {@link PlacementModifierType} only holds its modifier's codec, and 1.20.5 made
 * {@code PlacementModifierType.codec()} a {@link MapCodec} where it used to be a plain {@code Codec}. A mod declares the
 * codec once as a {@code MapCodec} ({@code RecordCodecBuilder.mapCodec} on every version) and gets the type from here,
 * to register under {@code Registries.PLACEMENT_MODIFIER_TYPE}.
 */
public final class Placements {

    private Placements() {}

    public static <P extends PlacementModifier> PlacementModifierType<P> type(MapCodec<P> codec) {
        //? if >=1.21 {
        return () -> codec;
        //?} else {
        /*return codec::codec;
        *///?}
    }
}
