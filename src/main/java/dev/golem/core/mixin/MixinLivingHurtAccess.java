package dev.golem.core.mixin;

import dev.golem.core.world.LivingHurtAccess;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Exposes {@link LivingEntity#lastHurt}; see {@link LivingHurtAccess}. */
@Mixin(LivingEntity.class)
public abstract class MixinLivingHurtAccess implements LivingHurtAccess {

    @Shadow
    protected float lastHurt;

    @Override
    public float golem$lastHurt() {
        return lastHurt;
    }

    //? if >=26.1 {
    @Shadow
    public abstract net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player getLastHurtByPlayer();

    @Override
    public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player golem$lastHurtByPlayer() {
        return getLastHurtByPlayer();
    }
    //?} else {
    /*@Shadow
    protected net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player lastHurtByPlayer;

    @Override
    public net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player golem$lastHurtByPlayer() {
        return lastHurtByPlayer;
    }
    *///?}
}
