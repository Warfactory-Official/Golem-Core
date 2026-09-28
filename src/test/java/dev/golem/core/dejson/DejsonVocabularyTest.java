package dev.golem.core.dejson;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The vocabulary added for the 2026-09-28 migration: every shape must write exactly the file it replaced. */
class DejsonVocabularyTest {

    @BeforeEach
    void clearRegistry() {
        Dejson.reset();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void elementGeometryKeepsIntegersAndFaceDetail() {
        Dejson.model("hbm:block/pipe_anchor_down", Model.parent("minecraft:block/block")
                .ambientOcclusion(false)
                .textures(Slots.of("all", "hbm:blocks/pipe_anchor", "particle", "hbm:blocks/pipe_anchor"))
                .elements(Model.Element.box(6, 0, 6, 10, 16.5, 10).rotation(8, 8, 8, "y", 22.5, true)
                        .face(Model.Face.of("north", "#all").uv(6, 0, 10, 16).cullface("north").rotation(90))));
        JsonObject model = DejsonWriter.files().get("assets/hbm/models/block/pipe_anchor_down.json");
        assertEquals(json("{'parent':'minecraft:block/block','ambientocclusion':false,"
                + "'textures':{'all':'hbm:blocks/pipe_anchor','particle':'hbm:blocks/pipe_anchor'},"
                + "'elements':[{'from':[6,0,6],'to':[10,16.5,10],"
                + "'rotation':{'origin':[8,8,8],'axis':'y','angle':22.5,'rescale':true},"
                + "'faces':{'north':{'uv':[6,0,10,16],'texture':'#all','cullface':'north','rotation':90}}}]}"), model);
        assertTrue(model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonArray("from").get(0)
                .getAsJsonPrimitive().getAsNumber() instanceof Integer);
        assertTrue(Dejson.sprites().contains("hbm:blocks/pipe_anchor"));
    }

    @Test
    void multipartAndVariantFilesAreWrittenUnderBlockstates() {
        States.multipart("hbm", "fence_metal")
                .part(new Variant("hbm:block/fence_metal_post"))
                .part(When.or(When.of("north", "true"), When.of("south", "true")),
                        new Variant("hbm:block/fence_metal_side", 0, 90, true))
                .register();
        assertEquals(json("{'multipart':[{'apply':{'model':'hbm:block/fence_metal_post'}},"
                        + "{'when':{'OR':[{'north':'true'},{'south':'true'}]},"
                        + "'apply':{'model':'hbm:block/fence_metal_side','y':90,'uvlock':true}}]}"),
                DejsonWriter.files().get("assets/hbm/blockstates/fence_metal.json"));
        assertThrows(DejsonException.class, () -> States.variants("hbm", "fence_metal")
                .variant("", new Variant("hbm:block/x")).register());
    }

    @Test
    void anItemDefinitionTreeIsEmittedWithTheOversizedFlag() {
        Dejson.item("hbm", "machine_press")
                .definition(ItemModels.special("hbm:item/machine_obj_base",
                        ItemModels.custom("hbm:machine_obj").with("obj", "hbm:models/press.obj").with("yaw", 90)))
                .oversizedInGui()
                .register();
        assertEquals(json("{'model':{'type':'minecraft:special','base':'hbm:item/machine_obj_base',"
                        + "'model':{'type':'hbm:machine_obj','obj':'hbm:models/press.obj','yaw':90}},"
                        + "'oversized_in_gui':true}"),
                DejsonWriter.files().get("assets/hbm/items/machine_press.json"));
    }

    @Test
    void dataShapesWriteTheirFiles() {
        Tags.of("item", "hbm", "repairs/cobalt").register();
        Tags.of("block", "hbm", "fbi_breakable").values("minecraft:chest").optional("hbm:crate_iron").register();
        Equipment.humanoid("hbm", "steel", "hbm:steel");
        Atlases.directories("minecraft", "blocks", "blocks", "blocks/");
        DataMaps.furnaceFuels().burnTime("hbm:lignite", 1200).register();
        Biomes.biome("hbm", "crater").downfall(0.0).precipitation(false).sky(0x525A52)
                .spawn(Biomes.MONSTER, "minecraft:witch", 5, 1, 1).register();

        assertEquals(json("{'replace':false,'values':[]}"),
                DejsonWriter.files().get("data/hbm/tags/item/repairs/cobalt.json"));
        assertEquals(json("{'replace':false,'values':['minecraft:chest',{'id':'hbm:crate_iron','required':false}]}"),
                DejsonWriter.files().get("data/hbm/tags/block/fbi_breakable.json"));
        assertEquals(json("{'layers':{'humanoid':[{'texture':'hbm:steel'}],'humanoid_leggings':[{'texture':'hbm:steel'}]}}"),
                DejsonWriter.files().get("assets/hbm/equipment/steel.json"));
        assertEquals(json("{'sources':[{'type':'minecraft:directory','source':'blocks','prefix':'blocks/'}]}"),
                DejsonWriter.files().get("assets/minecraft/atlases/blocks.json"));
        assertEquals(json("{'values':{'hbm:lignite':{'burn_time':1200}}}"),
                DejsonWriter.files().get("data/neoforge/data_maps/item/furnace_fuels.json"));
        JsonObject biome = DejsonWriter.files().get("data/hbm/worldgen/biome/crater.json");
        assertEquals("#525a52", biome.getAsJsonObject("attributes").get("minecraft:visual/sky_color").getAsString());
        assertEquals(11, biome.getAsJsonArray("features").size());
        assertEquals(1, biome.getAsJsonObject("spawners").getAsJsonArray("monster").size());
    }

    @Test
    void lootShapesAddedForTheMigration() {
        Drops.selfWhen("hbm", "door_metal", "half", "lower");
        Drops.stacks("hbm", "zirnox_destroyed", "hbm:concrete_smooth", 6);
        JsonObject door = DejsonWriter.files().get("data/hbm/loot_table/blocks/door_metal.json");
        JsonObject entry = door.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject();
        assertEquals("lower", entry.getAsJsonArray("conditions").get(0).getAsJsonObject()
                .getAsJsonObject("properties").get("half").getAsString());
        JsonObject wreck = DejsonWriter.files().get("data/hbm/loot_table/blocks/zirnox_destroyed.json");
        assertEquals(6, wreck.getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject().getAsJsonArray("functions").get(0).getAsJsonObject().get("count").getAsInt());
    }
}
