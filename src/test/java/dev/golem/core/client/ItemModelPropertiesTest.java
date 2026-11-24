package dev.golem.core.client;

//? if >=1.21.11 {
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemModelPropertiesTest {
    /** ItemStack's static init reads the item registry, which 1.21.11 only allows after bootstrap. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
