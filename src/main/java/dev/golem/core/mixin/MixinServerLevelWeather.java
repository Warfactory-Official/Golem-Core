package dev.golem.core.mixin;

import dev.golem.core.world.Weathers;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link Weathers}: a level whose weather a mod provides skips vanilla's weather cycle that tick and takes the
 * provider's rain and thunder instead.
 *
 * <p>Vanilla drives every sky-lit dimension's rain and thunder from the server's
 * one weather data and has no per-level weather, and no loader has a weather event. Setting
 * the levels from a level-tick event afterwards fights vanilla's cycle every tick (it moves them back by 0.01
 * and sends its own packets, START/STOP_RAINING included, so the client's rain flickers). Widening the fields would
 * still need a write at the moment vanilla advances them. One cancellable HEAD {@code @Inject} on the private
 * {@code advanceWeatherCycle} (the same name on 1.20.1, 1.21.1 and 26.1); it cancels only for a level a provider
 * answers for, so every other level runs vanilla's cycle untouched.
 * The wake inject runs before vanilla tests for rain: NeoForge and Forge SleepFinishedTimeEvent fire only while the
 * daylight rule advances time, and Fabric's sleep events are per player.
 */
@Mixin(ServerLevel.class)
public abstract class MixinServerLevelWeather {

    @Inject(method = "wakeUpAllPlayers", at = @At("HEAD"))
    private void golem$sleptThrough(CallbackInfo ci) {
        Weathers.sleptThrough((ServerLevel) (Object) this);
    }

    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void golem$providedWeather(CallbackInfo ci) {
        if (Weathers.advance((ServerLevel) (Object) this)) ci.cancel();
    }
}
