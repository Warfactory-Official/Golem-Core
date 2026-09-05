package dev.golem.core.data;

//? if >=1.21.1 {
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
//?}

//? if <1.21.1 {
/*import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GolemComponentRoundTripTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void scalarAndCompoundValuesUseNamespacedItemNbt() {
        ItemStack stack = ItemStack.EMPTY.copy();
        GolemComponent<Integer> count = GolemComponent.integer("hbm", "count");
        GolemComponent<CompoundTag> data = GolemComponent.compound("hbm", "data");
        CompoundTag value = new CompoundTag();
        value.putString("material", "uranium");

        count.set(stack, 4);
        data.set(stack, value);

        assertEquals(4, count.getOr(stack, 0));
        assertEquals("uranium", data.get(stack).orElseThrow().getString("material"));
        assertTrue(stack.getTag().contains("hbm:count"));
        assertEquals(5, count.update(stack, 0, old -> old + 1));
        assertTrue(count.remove(stack).isPresent());
        assertFalse(count.has(stack));
    }

    @Test
    void itemStackValuesRoundTrip() {
        ItemStack owner = new ItemStack(Items.DIRT);
        GolemComponent<ItemStack> filter = GolemComponent.itemStack("hbm", "filter");

        filter.set(owner, new ItemStack(Items.STONE));

        assertTrue(filter.has(owner));
        assertEquals(Items.STONE, filter.get(owner).orElseThrow().getItem());
    }
}
*///?}

//? if >=1.21.1 {
class GolemComponentRoundTripTest {

    @Test
    void persistentComponentsAlsoHaveARegistryAwareNetworkCodec() {
        var stream = GolemComponent.componentType(Codec.INT).streamCodec();
        assertNotNull(stream);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            stream.encode(buffer, 37);
            assertEquals(37, stream.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
//?}
