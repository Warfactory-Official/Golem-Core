package dev.golem.core.dejson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DejsonRegistryTest {

    @BeforeEach
    void clearRegistry() {
        Dejson.reset();
    }

    @Test
    void aDeclarationPublishesItsModelsBlockstateAndItem() {
        Dejson.block("hbm", "crate_steel")
                .models(Models.cubeAll("hbm:blocks/crate_steel"))
                .states(Variants.facing())
                .blockItem()
                .register();

        assertNotNull(Dejson.model("hbm:block/crate_steel"));
        assertEquals(Fixtures.json("{'parent':'hbm:block/crate_steel'}"),
                Dejson.model("hbm:item/crate_steel"));
        assertEquals(Fixtures.blockState("crate_steel"), Dejson.blockState("hbm:crate_steel"));
        assertEquals(Set.of("hbm:blocks/crate_steel"), Dejson.sprites());
    }

    @Test
    void aStairDeclarationPublishesAllThreeModels() {
        Dejson.block("hbm", "brick_compound_stairs")
                .models(Models.stairsAll("hbm:blocks/brick_compound"))
                .states(Variants.stairs())
                .blockItem()
                .register();

        assertEquals(Set.of("hbm:block/brick_compound_stairs",
                        "hbm:block/brick_compound_stairs_inner",
                        "hbm:block/brick_compound_stairs_outer",
                        "hbm:item/brick_compound_stairs"),
                Dejson.modelIds());
        assertEquals(Fixtures.blockState("brick_compound_stairs"),
                Dejson.blockState("hbm:brick_compound_stairs"));
    }

    @Test
    void aBlockstateNamingAModelTheShapeNeverEmitsIsRefused() {
        DejsonException failure = assertThrows(DejsonException.class, () ->
                Dejson.block("hbm", "half_slab")
                        .models(Models.cubeAll("hbm:blocks/x"))
                        .states(Variants.slab())
                        .register());

        assertTrue(failure.getMessage().contains("hbm:block/half_slab_top"), failure.getMessage());
        assertNull(Dejson.blockState("hbm:half_slab"), "a refused declaration must not publish");
    }

    @Test
    void anIncompleteDeclarationIsRefused() {
        assertThrows(DejsonException.class, () ->
                Dejson.block("hbm", "a").states(Variants.simple()).register());
        assertThrows(DejsonException.class, () ->
                Dejson.block("hbm", "b").models(Models.cubeAll("hbm:blocks/b")).register());
    }

    @Test
    void declaringTheSameBlockOrModelTwiceIsRefused() {
        Dejson.block("hbm", "a").models(Models.cubeAll("hbm:blocks/a"))
                .states(Variants.simple()).register();

        assertThrows(DejsonException.class, () -> Dejson.block("hbm", "a")
                .models(Models.cubeAll("hbm:blocks/a")).states(Variants.simple()).register());
        DejsonException collision = assertThrows(DejsonException.class, () -> Dejson.block("hbm", "b")
                .models(base -> {
                    java.util.Map<String, com.google.gson.JsonObject> models = new java.util.LinkedHashMap<>();
                    models.put(base, Models.json(Models.CUBE_ALL, Slots.of("all", "hbm:blocks/b")));
                    models.put("hbm:block/a", Models.json(Models.CUBE_ALL, Slots.of("all", "hbm:blocks/a")));
                    return models;
                })
                .states(Variants.simple())
                .register());

        assertTrue(collision.getMessage().contains("hbm:block/a"), collision.getMessage());
        assertNull(Dejson.blockState("hbm:b"), "a refused declaration must not half-register");
        assertNull(Dejson.model("hbm:block/b"), "a refused declaration must not publish its models");
    }

    @Test
    void aBlockItemAlsoPublishesTheModernItemDefinition() {
        Dejson.block("hbm", "waste_log")
                .models(Models.column("hbm:blocks/waste_log_top", "hbm:blocks/waste_log_side"))
                .states(Variants.axis())
                .blockItem()
                .register();

        assertEquals(Fixtures.json("{'parent':'hbm:block/waste_log'}"),
                Dejson.model("hbm:item/waste_log"));
        assertEquals(Fixtures.json("{'model':{'type':'minecraft:model','model':'hbm:item/waste_log'}}"),
                Dejson.itemDefinition("hbm:item/waste_log"));
    }

    @Test
    void aBlockWithNoItemPublishesNoItemDefinition() {
        Dejson.block("hbm", "fire_digamma")
                .models(Models.cubeAll("hbm:blocks/fire_digamma"))
                .states(Variants.simple())
                .register();

        assertNull(Dejson.itemDefinition("hbm:item/fire_digamma"));
    }

    @Test
    void slotReferencesAreNotReportedAsSprites() {
        Dejson.block("hbm", "ref")
                .models(Models.parented(Models.CUBE_BOTTOM_TOP,
                        Slots.of("side", "hbm:blocks/ref").and("top", "#side").and("bottom", "#side")))
                .states(Variants.simple())
                .register();

        assertEquals(Set.of("hbm:blocks/ref"), Dejson.sprites());
    }

    @Test
    void declaredPropertiesAndKeysAreVisibleForTheCoverageCheck() {
        Dejson.Declaration declaration = Dejson.block("hbm", "s")
                .models(Models.stairsAll("hbm:blocks/s"))
                .states(Variants.stairs())
                .register();

        assertEquals(Set.of("facing", "half", "shape"), declaration.properties());
        assertEquals(40, declaration.variantKeys().size());
        assertTrue(declaration.dispatch().covers(java.util.Map.of(
                "facing", "north", "half", "top", "shape", "outer_left", "waterlogged", "false")));
    }
}
