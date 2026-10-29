package dev.golem.core.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

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

}
