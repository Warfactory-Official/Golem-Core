package dev.golem.core.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import dev.golem.core.client.Sounds;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.SOFTSourceSpatialize;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla has no setup hook; Forge-family source events follow playback, and Fabric has none.
 * Widening exposes fields but cannot insert a callback before playback.
 */
@Mixin(SoundEngine.class)
public abstract class MixinWorldSoundSpatialization {
    @ModifyArg(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V",
            ordinal = 0), index = 0)
    private Consumer<Channel> golem$spatialization(Consumer<Channel> setup,
                                                   @Local(argsOnly = true) SoundInstance instance) {
        boolean positional = Sounds.spatializes(instance)
                && !(instance instanceof EntityBoundSoundInstance bound
                && bound.entity == Minecraft.getInstance().getCameraEntity());
        return channel -> {
            boolean spatialize = AL.getCapabilities().AL_SOFT_source_spatialize;
            int source = Sounds.source(channel);
            if (spatialize) AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT,
                    SOFTSourceSpatialize.AL_AUTO_SOFT);
            setup.accept(channel);
            if (positional && spatialize)
                AL10.alSourcei(source, SOFTSourceSpatialize.AL_SOURCE_SPATIALIZE_SOFT, AL10.AL_TRUE);
        };
    }
}
