package dev.golem.core.mixin;

//? if fabric && <1.20.5 {

/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.world.ItemStacks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * Fabric 1.20.1 only: the per-stack stack size {@link ItemStacks#setMaxStackSize} gives every other node.
 *
 * <p>Ladder (AGENTS.md §7): 1.20.1 vanilla asks the ITEM for its limit ({@code getItem().getMaxStackSize()}),
 * with no stack in hand, so no vanilla override can answer per stack; 1.20.5+ has the
 * {@code max_stack_size} component. Forge patches in {@code IForgeItem.getMaxStackSize(ItemStack)} (used by
 * {@code CompatItem}), but Fabric API 1.20.1 has no per-stack limit hook, and widening access changes nothing
 * because the method is already public and has no stack parameter to consult. So one MixinExtras
 * {@code @ModifyReturnValue} reads the tag the facade wrote; a stack without it keeps vanilla's answer. It chains
 * with stack-size mods' hooks on the same getter, where a cancellable RETURN inject could return past them.
 ^/
@Mixin(ItemStack.class)
public abstract class MixinLegacyStackLimit {

    @ModifyReturnValue(method = "getMaxStackSize", at = @At("RETURN"))
    private int golem$perStackLimit(int limit) {
        return ItemStacks.legacyMaxStackSize((ItemStack) (Object) this, limit);
    }
}
*///?}
