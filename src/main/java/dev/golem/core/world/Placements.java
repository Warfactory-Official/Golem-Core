package dev.golem.core.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
//? if <26.3 {
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
//?}

/**
 * Placement-modifier type registration across the supported versions.
 *
 * <p>Like {@link Structures}: a {@link PlacementModifierType} only holds its modifier's codec, and since 1.20.5
 * {@code PlacementModifierType.codec()} is a {@link MapCodec}, not a plain {@code Codec}. A mod declares the
 * codec once as a {@code MapCodec} ({@code RecordCodecBuilder.mapCodec} on every version) and gets the type from here,
 * to register under {@code Registries.PLACEMENT_MODIFIER_TYPE}.
 */
public final class Placements {

    private Placements() {}

    //? if >=26.3 {
    /*/^* 26.3 registers the modifier's map codec itself under {@code Registries.PLACEMENT_MODIFIER_TYPE}. ^/
    public static <P extends PlacementModifier> MapCodec<P> type(MapCodec<P> codec) {
        return codec;
    }
    *///?} else {
    public static <P extends PlacementModifier> PlacementModifierType<P> type(MapCodec<P> codec) {
        //? if >=1.21 {
        return () -> codec;
        //?} else {
        /*return codec::codec;
        *///?}
    }
    //?}
}
