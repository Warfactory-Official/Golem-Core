package dev.golem.core.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Client sound operations whose manager method was renamed in the newest Minecraft API. */
public final class Sounds {
    private Sounds() {}

    public static void playDelayed(SoundInstance sound, int delay) {
        Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
    }
    /** OpenAL source owned by the live native channel. */
    public static int source(Channel channel) {
        return channel.source;
    }

    /**
     * A temporary gain (0 to 1) on a category, on top of its volume option, kept until set again: 26.1's
     * {@code SoundManager.updateCategoryVolume}. Playing sounds follow at once; a sound at gain 0 keeps playing silently.
     */
    public static void setCategoryGain(SoundSource source, float gain) {
        //? if >=1.21.11 {
        Minecraft.getInstance().getSoundManager().updateCategoryVolume(source, gain);
        //?} else {
        /*CategoryGains.set(source, gain);
        *///?}
    }

    /** A category's volume option times the master volume, as 26.1's {@code Options.getFinalSoundSourceVolume}. */
    public static float finalVolume(SoundSource source) {
        //? if >=1.21.11 {
        return Minecraft.getInstance().options.getFinalSoundSourceVolume(source);
        //?} else {
        /*net.minecraft.client.Options options = Minecraft.getInstance().options;
        return source == SoundSource.MASTER ? options.getSoundSourceVolume(source)
                : options.getSoundSourceVolume(SoundSource.MASTER) * options.getSoundSourceVolume(source);
        *///?}
    }
}
