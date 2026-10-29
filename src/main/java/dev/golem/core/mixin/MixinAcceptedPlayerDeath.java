package dev.golem.core.mixin;

import dev.golem.core.world.Deaths;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Vanilla has no accepted-death callback; loader death events still permit later vetoes.
// Widening cannot insert the notification into the player's separate death path.
@Mixin(ServerPlayer.class)
public abstract class MixinAcceptedPlayerDeath {
    //? if >=26.1 {
    @Inject(method = "die", at = @At(value = "FIELD", target =
            "Lnet/minecraft/world/level/gamerules/GameRules;SHOW_DEATH_MESSAGES:Lnet/minecraft/world/level/gamerules/GameRule;",
            opcode = Opcodes.GETSTATIC))
    //?} else {
    /*@Inject(method = "die", at = @At(value = "FIELD", target =
            "Lnet/minecraft/world/level/GameRules;RULE_SHOWDEATHMESSAGES:Lnet/minecraft/world/level/GameRules$Key;",
            opcode = Opcodes.GETSTATIC))
    *///?}
    private void golem$acceptedPlayerDeath(DamageSource source, CallbackInfo ci) {
        Deaths.accepted((LivingEntity) (Object) this, source);
    }
}
