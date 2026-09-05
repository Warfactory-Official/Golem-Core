package dev.golem.core.client;

import net.minecraft.client.resources.sounds.SoundInstance;

/** Client sound operations whose manager method was renamed in the newest Minecraft API. */
public final class Sounds {
    private Sounds() {}

    public static void playDelayed(SoundInstance sound, int delay) {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
    }
}
