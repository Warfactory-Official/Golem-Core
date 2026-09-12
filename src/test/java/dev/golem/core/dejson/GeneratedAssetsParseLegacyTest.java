package dev.golem.core.dejson;

//? if <26.1 {
/*import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import dev.golem.core.Ids;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The generated JSON through the game's own parsers on the pre-rewrite model system, where a model
// JSON is a BlockModel built by a Gson deserializer and a blockstate is a BlockModelDefinition.
// Both eras have to accept the same generated text, which is the whole reason the generator emits
// JSON rather than building unbaked models directly.
class GeneratedAssetsParseLegacyTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void parentedModelsKeepTheirParentAndSlots() {
        BlockModel model = parse(Models.cubeBottomTop("hbm:blocks/c4_bottom",
                "hbm:blocks/c4_side", "hbm:blocks/c4_top").models("hbm:block/c4").get("hbm:block/c4"));

        assertTrue(model.getDependencies().contains(
                Ids.parse("minecraft:block/cube_bottom_top")));
        assertTrue(model.hasTexture("bottom"));
        assertTrue(model.hasTexture("side"));
        assertTrue(model.hasTexture("top"));
    }

    @Test
    void aSlotNothingReferencesSurvivesTheParse() {
        BlockModel model = parse(Models.parented(Models.CUBE_ALL,
                Slots.of("all", "hbm:blocks/deco_steel").and("ct", "hbm:blocks/deco_steel_ctm"))
                .models("hbm:block/deco_steel").get("hbm:block/deco_steel"));

        assertTrue(model.hasTexture("ct"),
                "a connected-texture slot carrier must survive generation and parsing");
        assertEquals("hbm:blocks/deco_steel_ctm", model.getMaterial("ct").texture().toString());
    }

    @Test
    void everyShapeInTheVocabularyParses() {
        for (Shape shape : new Shape[]{
                Models.cubeAll("hbm:blocks/a"),
                Models.cubeBottomTop("hbm:blocks/a", "hbm:blocks/b", "hbm:blocks/c"),
                Models.column("hbm:blocks/a", "hbm:blocks/b"),
                Models.orientable("hbm:blocks/a", "hbm:blocks/b", "hbm:blocks/c"),
                Models.cross("hbm:blocks/a"),
                Models.slabAll("hbm:blocks/a"),
                Models.slab("hbm:blocks/a", "hbm:blocks/b", "hbm:blocks/c"),
                Models.stairsAll("hbm:blocks/a"),
                Models.generated("hbm:items/a", "hbm:items/b"),
                Models.handheld("hbm:items/a"),
                Models.blockItem("hbm:block/a")}) {
            shape.models("hbm:block/example").forEach((id, json) -> assertTrue(
                    parse(json).getDependencies().contains(
                            Ids.parse(json.get("parent").getAsString())),
                    id + " lost its parent"));
        }
    }

    @Test
    void stairsCoverEveryStateOfARealStairBlock() {
        assertCoversEveryState(Blocks.OAK_STAIRS, Variants.stairs());
    }

    @Test
    void slabsCoverEveryStateOfARealSlabBlock() {
        assertCoversEveryState(Blocks.OAK_SLAB, Variants.slab());
    }

    @Test
    void axisCoversEveryStateOfARealLog() {
        assertCoversEveryState(Blocks.OAK_LOG, Variants.axis());
    }

    @Test
    void facingCrossedWithLitCoversEveryStateOfAFurnace() {
        assertCoversEveryState(Blocks.FURNACE,
                Variants.product(Variants.facing(), Variants.select("lit", "false", "_off", "true", "_on")));
    }

    @Test
    void aKeyThatOmitsAPropertyWildcardsIt() {
        assertEquals(24, Blocks.CHEST.getStateDefinition().getPossibleStates().size());
        assertCoversEveryState(Blocks.CHEST, Variants.facing());
    }

    @Test
    void aBlockstateMissingAValueIsCaughtBeforeItShips() {
        Dispatch incomplete = Variants.select("axis", "x", "_x", "y", "_y");

        assertEquals(java.util.List.of("axis=z"), BlockStates.uncovered(Blocks.OAK_LOG, incomplete));
    }

    @Test
    void aDispatchNamingAPropertyTheBlockLacksIsCaught() {
        assertEquals(java.util.List.of("lid"),
                BlockStates.unknownProperties(Blocks.OAK_LOG, Variants.uniform("lid", "none")));
    }

    private static void assertCoversEveryState(Block block, Dispatch dispatch) {
        StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> states =
                block.getStateDefinition();
        BlockModelDefinition.Context context = new BlockModelDefinition.Context();
        context.setDefinition(states);
        BlockModelDefinition definition = BlockModelDefinition.fromJsonElement(context,
                dispatch.toJson("hbm:block/example"));

        assertEquals(dispatch.placements().keySet(), definition.getVariants().keySet());
        for (String key : definition.getVariants().keySet()) {
            assertNotNull(definition.getVariant(key), key + " parsed to nothing");
            assertKeyNamesRealProperties(states, key);
        }
        assertEquals(java.util.List.of(), BlockStates.uncovered(block, dispatch));
    }

    // Mirrors what BlockStateModelLoader does when it turns a variant key into a state predicate:
    // an unknown property or an unparseable value is a hard error there, so it is one here.
    private static void assertKeyNamesRealProperties(
            StateDefinition<Block, net.minecraft.world.level.block.state.BlockState> states,
            String key) {
        if (key.isEmpty()) {
            return;
        }
        for (String term : key.split(",")) {
            int equals = term.indexOf('=');
            Property<?> property = states.getProperty(term.substring(0, equals));
            assertNotNull(property, "unknown property in variant key " + key);
            assertTrue(property.getValue(term.substring(equals + 1)).isPresent(),
                    "unknown value in variant key " + key);
        }
    }

    private static BlockModel parse(JsonObject json) {
        BlockModel model = BlockModel.fromString(json.toString());
        assertNotNull(model);
        return model;
    }

    private static Set<String> unused() {
        return Set.of();
    }
}
*///?}
