package dev.golem.core.mixin;

//? if fabric && <1.20.5 {

/*import dev.golem.core.world.ItemStacks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * Fabric 1.20.1 only: the per-stack stack size {@link ItemStacks#setMaxStackSize} gives every other node.
 *
 * <p>Ladder (AGENTS.md §7): 1.20.1 vanilla asks the ITEM for its limit ({@code getItem().getMaxStackSize()}),
 * with no stack in hand, so no vanilla override can answer per stack; 1.20.5 replaced this with the
 * {@code max_stack_size} component. Forge patches in {@code IForgeItem.getMaxStackSize(ItemStack)} (used by
 * {@code CompatItem}), but Fabric API 1.20.1 has no per-stack limit hook, and widening access changes nothing
 * because the method is already public and simply has no stack parameter to consult. So one {@code RETURN}
 * inject reads the tag the facade wrote; a stack without it keeps vanilla's answer.
 ^/
@Mixin(ItemStack.class)
public abstract class MixinLegacyStackLimit {

    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true)
    private void golem$perStackLimit(CallbackInfoReturnable<Integer> cir) {
        int limit = ItemStacks.legacyMaxStackSize((ItemStack) (Object) this, cir.getReturnValueI());
        if (limit != cir.getReturnValueI()) cir.setReturnValue(limit);
    }
}
*///?}
