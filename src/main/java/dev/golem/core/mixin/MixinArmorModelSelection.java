package dev.golem.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.golem.core.world.ArmorModelSelection;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
//? if >=26.1 {
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
//?}

/**
 * Vanilla lacks item-aware geometry selection and loader hooks replace the whole model.
 * Widening visibility cannot supply the missing renderer-context callback.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class MixinArmorModelSelection {
    //? if >=26.1 {
    @ModifyArg(method = "getArmorModel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/ArmorModelSet;get(Lnet/minecraft/world/entity/EquipmentSlot;)Ljava/lang/Object;"), index = 0)
    private EquipmentSlot golem$armorModelSlot(EquipmentSlot slot, @Local(argsOnly = true) HumanoidRenderState state) {
        var stack = switch (slot) {
            case HEAD -> state.headEquipment;
            case CHEST -> state.chestEquipment;
            case LEGS -> state.legsEquipment;
            case FEET -> state.feetEquipment;
            default -> throw new IllegalArgumentException("Not a humanoid armor slot: " + slot);
        };
        return stack.getItem() instanceof ArmorModelSelection selection ? selection.armorModelSlot() : slot;
    }
    //?} else {
    /*@ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;getArmorModel(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/client/model/HumanoidModel;"), index = 0)
    private EquipmentSlot golem$armorModelSlot(EquipmentSlot slot, @Local(argsOnly = true) LivingEntity entity) {
        var stack = entity.getItemBySlot(slot);
        return stack.getItem() instanceof ArmorModelSelection selection ? selection.armorModelSlot() : slot;
    }
    *///?}
}
