package dev.golem.core.client;

//? if >=26.1 {
/*import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemModelPropertiesTest {
    @BeforeAll
    static void bootstrapItemModels() {
        ItemModels.bootstrap();
    }

    @Test
    void decodedPropertyRetainsRegisteredCallback() {
        ItemModelProperties.VariantProperty registered =
                new ItemModelProperties.VariantProperty(stack -> 37);

        var decoded = registered.type().switchCodec().codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "property": {},
                  "cases": [
                    {"when": "37", "model": {"type": "minecraft:empty"}}
                  ]
                }
                """));

        assertEquals("37", decoded.result().orElseThrow().property()
                .get(ItemStack.EMPTY, null, null, 0, null));
    }
}
*///?}
