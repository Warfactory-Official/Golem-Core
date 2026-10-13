package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.golem.core.world.Gliders;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link Gliders#wingsFor}: the cape hides behind wings drawn for an elytra stored in the chest item, as it hides
 * behind a worn elytra (26.1: the chest asset has a wings layer; before: the chest item is an elytra). The companion of
 * {@link MixinGliderWings}, with the same justification. Listed on every loader (client).
 */
@Mixin(CapeLayer.class)
public abstract class MixinGliderCape {

    //? if >=26.1 {
    @ModifyExpressionValue(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At(value = "FIELD", target = "chestEquipment:Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack golem$wings(ItemStack chest) {
        return Gliders.wingsFor(chest);
    }
    //?} else {
    /*@ModifyExpressionValue(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack golem$wings(ItemStack chest) {
        return Gliders.wingsFor(chest);
    }
    *///?}
}
