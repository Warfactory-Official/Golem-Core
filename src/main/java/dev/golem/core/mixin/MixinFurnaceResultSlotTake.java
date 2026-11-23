package dev.golem.core.mixin;

import dev.golem.core.world.Smelts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric's {@link Smelts#onTake} hook, where Forge and NeoForge fire {@code ItemSmeltedEvent}.
 *
 * <p>Why a mixin (AGENTS.md section 7): vanilla's only callback is the result item's own {@code onCraftedBy}, which a
 * mod cannot override for vanilla items such as iron ingots; Fabric API has no smelting event; widening cannot help,
 * the call is inside {@code checkTakeAchievements}. One {@code @Inject} at its head, same descriptor on every version.
 */
@Mixin(FurnaceResultSlot.class)
public abstract class MixinFurnaceResultSlotTake {

    @Shadow
    @Final
    private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void golem$smelted(ItemStack stack, CallbackInfo ci) {
        Smelts.taken(player, stack);
    }
}
