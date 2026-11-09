package dev.golem.core.mixin;

import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import dev.golem.core.client.Sounds;
import dev.golem.core.client.StereoFalloff;
import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives stereo sources the distance gain OpenAL skips ({@link StereoFalloff}), refreshed each tick by {@code updateStream}.
 * Rungs 1-3: vanilla sets a one-shot's gain once, NeoForge's {@code PlaySoundSourceEvent} fires only at start and
 * fabric-api has no sound event, and visibility is not the obstacle. TAIL injections that only re-set the gain.
 */
@Mixin(Channel.class)
public abstract class MixinChannelStereoFalloff {

    @Unique
    private boolean golem$stereo;
    @Unique
    private float golem$base = 1F;
    @Unique
    private float golem$maxDistance;

    @Inject(method = "setVolume", at = @At("TAIL"))
    private void golem$volume(float volume, CallbackInfo ci) {
        golem$base = volume;
        golem$apply();
    }

    @Inject(method = "linearAttenuation", at = @At("TAIL"))
    private void golem$linear(float maxDistance, CallbackInfo ci) {
        golem$maxDistance = maxDistance;
        golem$apply();
    }

    @Inject(method = "disableAttenuation", at = @At("TAIL"))
    private void golem$unattenuated(CallbackInfo ci) {
        golem$maxDistance = 0F;
        golem$apply();
    }

    @Inject(method = "attachStaticBuffer", at = @At("TAIL"))
    private void golem$staticBuffer(SoundBuffer buffer, CallbackInfo ci) {
        golem$stereo = StereoFalloff.stereo(Sounds.source((Channel) (Object) this));
        golem$apply();
    }

    @Inject(method = "attachBufferStream", at = @At("TAIL"))
    private void golem$stream(AudioStream stream, CallbackInfo ci) {
        golem$stereo = stream.getFormat().getChannels() > 1;
        golem$apply();
    }

    @Inject(method = {"setSelfPosition", "updateStream"}, at = @At("TAIL"))
    private void golem$refresh(CallbackInfo ci) {
        golem$apply();
    }

    @Unique
    private void golem$apply() {
        if (!golem$stereo) return;
        int source = Sounds.source((Channel) (Object) this);
        AL10.alSourcef(source, AL10.AL_GAIN, StereoFalloff.gain(source, golem$base, golem$maxDistance));
    }
}
