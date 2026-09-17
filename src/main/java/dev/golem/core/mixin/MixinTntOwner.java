package dev.golem.core.mixin;

import dev.golem.core.world.TntOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Writes the primed TNT's private igniter; see {@link TntOwner}. */
@Mixin(PrimedTnt.class)
public abstract class MixinTntOwner implements TntOwner {

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void golem$onExplode(CallbackInfo ci) {
        if (golem$explode()) ci.cancel();
    }

    //? if >=26.1 {
    @Shadow
    private net.minecraft.world.entity.@Nullable EntityReference<LivingEntity> owner;

    @Override
    public void golem$setOwner(@Nullable LivingEntity value) {
        owner = net.minecraft.world.entity.EntityReference.of(value);
    }
    //?} else {
    /*@Shadow
    private @Nullable LivingEntity owner;

    @Override
    public void golem$setOwner(@Nullable LivingEntity value) {
        owner = value;
    }
    *///?}
}
