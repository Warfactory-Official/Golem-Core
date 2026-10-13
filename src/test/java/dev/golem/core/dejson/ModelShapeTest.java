package dev.golem.core.dejson;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelShapeTest {

    private static final String BASE = "hbm:block/example";

    @Test
    void cubeAllEmitsOneParentedModel() {
        Map<String, JsonObject> models = Models.cubeAll("hbm:blocks/example").models(BASE);

        assertEquals(Set.of(BASE), models.keySet());
        assertEquals(Fixtures.json("{'parent':'minecraft:block/cube_all',"
                + "'textures':{'all':'hbm:blocks/example'}}"), models.get(BASE));
    }

    @Test
    void slabEmitsBottomTopAndDouble() {
        Map<String, JsonObject> models = Models.slabAll("hbm:blocks/brick_compound")
                .models("hbm:block/brick_compound_slab");

        assertEquals(List.of("hbm:block/brick_compound_slab",
                        "hbm:block/brick_compound_slab_top",
                        "hbm:block/brick_compound_slab_double"),
                List.copyOf(models.keySet()));
        Fixtures.assertSameModel(Fixtures.model("brick_compound_slab"),
                models.get("hbm:block/brick_compound_slab"));
        Fixtures.assertSameModel(Fixtures.model("brick_compound_slab_top"),
                models.get("hbm:block/brick_compound_slab_top"));
        Fixtures.assertSameModel(Fixtures.model("brick_compound_slab_double"),
                models.get("hbm:block/brick_compound_slab_double"));
    }

    @Test
    void stairsEmitStraightInnerAndOuter() {
        Map<String, JsonObject> models = Models.stairsAll("hbm:blocks/brick_concrete_broken")
                .models("hbm:block/brick_concrete_broken_stairs");

        assertEquals(3, models.size());
        Fixtures.assertSameModel(Fixtures.model("brick_concrete_broken_stairs"),
                models.get("hbm:block/brick_concrete_broken_stairs"));
        assertEquals(Models.INNER_STAIRS, models.get("hbm:block/brick_concrete_broken_stairs_inner")
                .get("parent").getAsString());
        assertEquals(Models.OUTER_STAIRS, models.get("hbm:block/brick_concrete_broken_stairs_outer")
                .get("parent").getAsString());
    }

    @Test
    void columnAndCubeBottomTopMatchTheirSlotNames() {
        Fixtures.assertSameModel(Fixtures.model("basalt"),
                Models.column("hbm:blocks/basalt_top", "hbm:blocks/basalt")
                        .models("hbm:block/basalt").get("hbm:block/basalt"));
        Fixtures.assertSameModel(Fixtures.model("c4"),
                Models.cubeBottomTop("hbm:blocks/c4_bottom", "hbm:blocks/c4_side", "hbm:blocks/c4_top")
                        .models("hbm:block/c4").get("hbm:block/c4"));
    }

    @Test
    void itemShapesCoverGeneratedHandheldAndBlockItem() {
        assertEquals(Fixtures.json("{'parent':'minecraft:item/generated',"
                        + "'textures':{'layer0':'hbm:items/ammo_debug'}}"),
                Models.generated("hbm:items/ammo_debug").models("hbm:item/ammo_debug")
                        .get("hbm:item/ammo_debug"));
        assertEquals(Fixtures.json("{'parent':'minecraft:item/handheld',"
                        + "'textures':{'layer0':'hbm:items/blowtorch'}}"),
                Models.handheld("hbm:items/blowtorch").models("hbm:item/blowtorch")
                        .get("hbm:item/blowtorch"));
        assertEquals(Fixtures.json("{'parent':'hbm:block/ash_digamma'}"),
                Models.blockItem("hbm:block/ash_digamma").models("hbm:item/ash_digamma")
                        .get("hbm:item/ash_digamma"));
    }

    @Test
    void generatedNumbersItsLayersFromZero() {
        JsonObject model = Models.generated("hbm:items/a", "hbm:items/b")
                .models("hbm:item/two").get("hbm:item/two");

        assertEquals("hbm:items/a", model.getAsJsonObject("textures").get("layer0").getAsString());
        assertEquals("hbm:items/b", model.getAsJsonObject("textures").get("layer1").getAsString());
    }

    @Test
    void slotsAreEmittedEvenWhenNothingReferencesThem() {
        JsonObject model = Models.parented("hbm:block/ctm_base",
                Slots.of("all", "hbm:blocks/deco_steel").and("ct", "hbm:blocks/deco_steel_ctm"))
                .models(BASE).get(BASE);

        assertTrue(model.getAsJsonObject("textures").has("ct"));
        assertEquals("hbm:blocks/deco_steel_ctm",
                model.getAsJsonObject("textures").get("ct").getAsString());
    }

    @Test
    void slotReferencesAreAccepted() {
        Slots slots = Slots.of("side", "hbm:blocks/a").and("particle", "#side");

        assertEquals(Set.of("hbm:blocks/a"), slots.sprites());
    }

    @Test
    void malformedDeclarationsThrowAtTheDeclarationSite() {
        assertThrows(DejsonException.class, () -> Slots.of("All", "hbm:blocks/a"));
        assertThrows(DejsonException.class, () -> Slots.of("all", "hbm:Blocks/A"));
        assertThrows(DejsonException.class, () -> Slots.of("all", "#Side"));
        assertThrows(DejsonException.class, () -> Slots.of("all", "hbm:blocks/a").and("all", "x:y"));
        assertThrows(DejsonException.class, () -> Models.parented("hbm:a:b", Slots.EMPTY));
        assertThrows(DejsonException.class, Models::generated);
    }
}
