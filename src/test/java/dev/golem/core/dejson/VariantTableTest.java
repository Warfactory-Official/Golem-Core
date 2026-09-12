package dev.golem.core.dejson;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each rotation table against the file it was transcribed from. A table that is wrong by one
 * quarter turn compiles, bakes and ships; the only thing that catches it is this.
 */
class VariantTableTest {

    @Test
    void horizontalFacingMatchesCrateSteel() {
        assertEquals(Fixtures.blockState("crate_steel"),
                Variants.facing().toJson("hbm:block/crate_steel"));
    }

    @Test
    void axisMatchesWasteLog() {
        assertEquals(Fixtures.blockState("waste_log"),
                Variants.axis().toJson("hbm:block/waste_log"));
    }

    @Test
    void slabMatchesBrickCompoundSlab() {
        assertEquals(Fixtures.blockState("brick_compound_slab"),
                Variants.slab().toJson("hbm:block/brick_compound_slab"));
    }

    @Test
    void allFortyStairVariantsMatchBrickCompoundStairs() {
        assertEquals(40, Variants.stairs().placements().size());
        assertEquals(Fixtures.blockState("brick_compound_stairs"),
                Variants.stairs().toJson("hbm:block/brick_compound_stairs"));
    }

    @Test
    void facingCrossedWithTwoSelectorsMatchesTheSixteenVariantMachine() {
        Dispatch dispatch = Variants.product(
                Variants.facing(),
                Variants.select("lit", "false", "_off", "true", "_on"),
                Variants.select("ext", "false", "", "true", "_tall"));

        assertEquals(16, dispatch.placements().size());
        assertEquals(Set.of("facing", "lit", "ext"), dispatch.properties());
        assertEquals(Fixtures.blockState("machine_difurnace_off"),
                dispatch.toJson("hbm:block/machine_difurnace"));
    }

    @Test
    void sixWayFacingPitchesDownAndUp() {
        Map<String, List<Variant>> variants = Variants.facingAll().variants("hbm:block/x");

        assertEquals(6, variants.size());
        assertEquals(new Variant("hbm:block/x", 90, 0, false), variants.get("facing=down").get(0));
        assertEquals(new Variant("hbm:block/x", 270, 0, false), variants.get("facing=up").get(0));
        assertEquals(new Variant("hbm:block/x", 0, 90, false), variants.get("facing=east").get(0));
    }

    @Test
    void uniformPropertiesSelectOneModel() {
        Dispatch dispatch = Variants.uniform("lid", "none", "concrete", "glass");

        assertEquals(3, dispatch.placements().size());
        assertEquals(Set.of("hbm:block/rbmk_absorber_column_none"),
                dispatch.referencedModels("hbm:block/rbmk_absorber_column_none"));
    }

    @Test
    void anIgnoredPropertyCrossedWithAPoolIsTheShadedOre() {
        Dispatch dispatch = Variants.product(Variants.uniformRange("shade", 0, 9), Variants.pool(4));

        assertEquals(10, dispatch.placements().size());
        assertEquals(List.of(new Variant("hbm:block/ore_0"), new Variant("hbm:block/ore_1"),
                        new Variant("hbm:block/ore_2"), new Variant("hbm:block/ore_3")),
                dispatch.variants("hbm:block/ore").get("shade=7"));
        assertTrue(dispatch.toJson("hbm:block/ore").getAsJsonObject("variants")
                .get("shade=0").isJsonArray());
    }

    @Test
    void intRangeSelectsByValue() {
        Dispatch dispatch = Variants.intRange("layers", 1, 8);

        assertEquals(8, dispatch.placements().size());
        assertEquals(new Variant("hbm:block/foam_layer_3"),
                dispatch.variants("hbm:block/foam_layer").get("layers=3").get(0));
    }

    @Test
    void keysMatchOnTheirOwnPropertiesAndWildcardTheRest() {
        Dispatch stairs = Variants.stairs();

        assertTrue(stairs.covers(Map.of("facing", "east", "half", "bottom",
                "shape", "straight", "waterlogged", "true")));
        assertFalse(stairs.covers(Map.of("facing", "east", "half", "bottom", "shape", "coiled")));
        assertTrue(Variants.simple().covers(Map.of("anything", "at_all")));
    }

    @Test
    void productRefusesToCrossTwoDispatchesOverTheSameProperty() {
        assertThrows(DejsonException.class,
                () -> Variants.product(Variants.facing(), Variants.facing()));
    }

    @Test
    void rotationsMustBeQuarterTurns() {
        assertThrows(DejsonException.class, () -> Placement.rotated(0, 45, false));
        assertThrows(DejsonException.class, () -> new Variant("hbm:block/x", 30, 0, false));
    }

    @Test
    void uvlockFollowsRotationOnStairsAndNeverOnPlainFacing() {
        Map<String, List<Variant>> stairs = Variants.stairs().variants("hbm:block/s");
        assertFalse(stairs.get("facing=east,half=bottom,shape=straight").get(0).uvlock());
        assertTrue(stairs.get("facing=south,half=bottom,shape=straight").get(0).uvlock());

        Map<String, List<Variant>> facing = Variants.facing().variants("hbm:block/f");
        assertFalse(facing.get("facing=west").get(0).uvlock());
        assertEquals(270, facing.get("facing=west").get(0).y());
    }
}
