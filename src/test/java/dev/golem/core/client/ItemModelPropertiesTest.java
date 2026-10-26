package dev.golem.core.client;

//? if >=26.1 {
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemModelPropertiesTest {
    @Test
    void propertyRetainsCallbackAndValueCodec() {
        ItemModelProperties.SelectProperty registered =
                new ItemModelProperties.SelectProperty(stack -> "37");

        assertEquals("37", registered.get(ItemStack.EMPTY, null, null, 0, null));
        assertEquals("37", registered.valueCodec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString("\"37\""))
                .result().orElseThrow());
    }
}
//?}
