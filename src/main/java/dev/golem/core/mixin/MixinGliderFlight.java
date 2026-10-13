package dev.golem.core.mixin;

import dev.golem.core.world.Gliders;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * {@link Gliders}: elytra flight granted by something other than an elytra in the chest slot (NeoForge and Forge).
 *
 * <p>Why a mixin. Rung 1, vanilla: 26.1's {@code LivingEntity.canGlide} accepts only items carrying the
 * {@code glider} component in their own equippable slot and wears that item; below 26.1 the check is the chest item's
 * {@code canElytraFly}. Neither can be answered for an elytra stored inside another item (a chestplate). Rung 2,
 * loaders: Fabric has {@code EntityElytraEvents.CUSTOM} and uses it (no mixin there); NeoForge 26.1 has no elytra hook
 * at all, and NeoForge 1.21.1 / Forge 1.20.1 offer only the item-side {@code IItemExtension.canElytraFly} and
 * {@code elytraFlightTick}, which a mod can implement for its own items but not for a vanilla or another mod's
 * chestplate. Rung 3, wideners: visibility is not the problem. The smallest mixin answers the one question at the one
 * place vanilla asks it (the same two points fabric-api's own elytra mixin injects at on 26.1); flight physics,
 * firework boosts and stop conditions stay vanilla's.
 *
 * <p>26.1: {@code canGlide} (after the ground/passenger/levitation checks, where it loops over the slots) and
 * {@code updateFallFlying}'s damage point (where vanilla would pick a glider to wear; with only a {@link Gliders}
 * source there is none, and {@code Util.getRandom} of an empty list would throw). Below 26.1: the chest item's
 * {@code canElytraFly}/{@code elytraFlightTick} in {@code updateFallFlying}; the start checks are in
 * {@link MixinGliderStart} and {@link MixinGliderClientStart}. Listed on NeoForge and Forge only.
 */
@Mixin(LivingEntity.class)
public abstract class MixinGliderFlight {

    //? if >=26.1 {
    @org.spongepowered.asm.mixin.injection.Inject(method = "canGlide", cancellable = true, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/EquipmentSlot;VALUES:Ljava/util/List;",
            opcode = org.objectweb.asm.Opcodes.GETSTATIC))
    private void golem$canGlide(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (Gliders.canGlide((LivingEntity) (Object) this)) cir.setReturnValue(true);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "updateFallFlying", cancellable = true, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Util;getRandom(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/lang/Object;"))
    private void golem$wear(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (Gliders.wearNow((LivingEntity) (Object) this)) ci.cancel();
    }
    //?} elif !fabric {
    /*@com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "updateFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;canElytraFly(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean golem$canFly(boolean vanilla) {
        return vanilla || Gliders.canGlide((LivingEntity) (Object) this);
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "updateFallFlying", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;elytraFlightTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean golem$flightTick(net.minecraft.world.item.ItemStack chest, LivingEntity entity, int flightTicks,
                                     com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if (chest.canElytraFly(entity)) return original.call(chest, entity, flightTicks);
        return Gliders.flightTick(entity, flightTicks);
    }
    *///?}
}
