package dev.golem.core.mixin;

//? if fabric && <1.20.5 {

/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/^*
 * Fabric 1.20.1 only: shift-click merges into a partly filled slot up to the slot's own limit, as Forge 1.20.1 and
 * vanilla 1.20.5+ do. Vanilla 1.20.1 caps the merge at the item's limit alone, so a one-item slot takes a whole stack.
 * Fabric API has no hook there, overriding moveItemStackTo would need every menu to share one base class, and widening
 * changes no logic.
 ^/
@Mixin(AbstractContainerMenu.class)
public abstract class MixinLegacySlotMerge {

    @ModifyExpressionValue(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I"))
    private int golem$slotLimit(int itemLimit, @Local(ordinal = 0) Slot slot) {
        return Math.min(itemLimit, slot.getMaxStackSize());
    }
}
*///?}
