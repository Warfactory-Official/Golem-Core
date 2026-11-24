package dev.golem.core.world;

//? if >=1.21.11 && <26.1 {
/*import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/^*
 * 26.1's {@code ItemStackTemplate} for 1.21.11, which has none: a replacement rule spells the vanilla name as this
 * class below 26.1. It holds a private copy of a stack, and the codecs are the stack's (the same JSON shape).
 ^/
public record StackTemplate(ItemStack stack) {

    public static final Codec<StackTemplate> CODEC = ItemStack.CODEC.xmap(StackTemplate::fromNonEmptyStack, StackTemplate::create);
    public static final StreamCodec<RegistryFriendlyByteBuf, StackTemplate> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(StackTemplate::fromNonEmptyStack, StackTemplate::create);

    public static StackTemplate fromNonEmptyStack(ItemStack stack) {
        return new StackTemplate(stack.copy());
    }

    public ItemStack create() {
        return stack.copy();
    }
}
*///?} else {
/** Unused on this node; see the 1.21.11 branch. */
final class StackTemplate {
    private StackTemplate() {}
}
//?}
