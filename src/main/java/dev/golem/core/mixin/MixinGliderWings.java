package dev.golem.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.golem.core.world.Gliders;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link Gliders#wingsFor}: vanilla's wings layer draws the wings of an elytra stored in the chest item (an elytra
 * installed in a chestplate), exactly as for a worn elytra - same model, animation, cape/elytra skin and glint.
 *
 * <p>Why a mixin. Rung 1, vanilla: the wings layer reads the chest stack itself (26.1 {@code WingsLayer}:
 * {@code state.chestEquipment}'s equippable asset; before: {@code ElytraLayer}: {@code getItemBySlot(CHEST)}), with no
 * way to name another stack. Rung 2, loaders: Forge/NeoForge's {@code ElytraLayer.shouldRender} and fabric-api's
 * {@code FabricElytraItem} only serve a mod's own chest item; NeoForge 26.1 and Fabric have no wings hook. Rung 3:
 * visibility is not the problem. A second wings layer would have to copy vanilla's per-era model, animation and
 * texture choice; substituting the one stack the layer reads keeps all of it vanilla's. Listed on every loader (client).
 */
//? if >=26.1 {
@Mixin(net.minecraft.client.renderer.entity.layers.WingsLayer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.layers.ElytraLayer.class)
*///?}
public abstract class MixinGliderWings {

    //? if >=26.1 {
    @ModifyExpressionValue(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "FIELD", target = "chestEquipment:Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack golem$wings(ItemStack chest) {
        return Gliders.wingsFor(chest);
    }
    //?} else {
    /*@ModifyExpressionValue(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack golem$wings(ItemStack chest) {
        return Gliders.wingsFor(chest);
    }
    *///?}
}
