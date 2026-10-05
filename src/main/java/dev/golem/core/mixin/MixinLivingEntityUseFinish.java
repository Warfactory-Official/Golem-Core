package dev.golem.core.mixin;

import dev.golem.core.world.ItemUses;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric's {@link ItemUses#onFinish} hook: the copy of the used stack taken just before vanilla's
 * {@code ItemStack.finishUsingItem}, handed to the handlers just after it - where Forge and NeoForge fire
 * {@code LivingEntityUseItemEvent.Finish}.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no finished-use callback outside the item's own
 * {@code finishUsingItem}, and the legacy behaviour applies to every food, vanilla ones included; (2) Fabric API has no
 * finish-using event ({@code UseItemCallback} is the start of a use), while Forge-like loaders use their event, so this
 * class is listed only in {@code golemcore.fabric.mixins.json}; (3) widening cannot help, the call happens inside
 * {@code completeUsingItem}. Two {@code @Inject}s around the one {@code finishUsingItem} call; the target descriptor is
 * the same on every supported version.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityUseFinish {

    @Shadow
    protected ItemStack useItem;

    @Unique
    private ItemStack golem$finishCopy = ItemStack.EMPTY;

    @Inject(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private void golem$beforeFinish(CallbackInfo ci) {
        golem$finishCopy = useItem.copy();
    }

    @Inject(method = "completeUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            shift = At.Shift.AFTER))
    private void golem$afterFinish(CallbackInfo ci) {
        ItemStack before = golem$finishCopy;
        golem$finishCopy = ItemStack.EMPTY;
        ItemUses.finished((LivingEntity) (Object) this, before);
    }
}
