package dev.golem.core.mixin;

import dev.golem.core.world.Deaths;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla has no accepted-death callback; loader death events still permit later vetoes.
// Widening cannot insert the notification required before loot.
@Mixin(LivingEntity.class)
public abstract class MixinAcceptedLivingDeath {
    @Inject(method = "die", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;dead:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void golem$acceptedDeath(DamageSource source, CallbackInfo ci) {
        Deaths.accepted((LivingEntity) (Object) this, source);
    }
}
