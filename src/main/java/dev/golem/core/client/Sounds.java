package dev.golem.core.client;

import com.mojang.blaze3d.audio.Channel;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Client sound operations whose manager method was renamed in the newest Minecraft API.
 */
public final class Sounds {
    private static final Set<String> SPATIAL_NAMESPACES = new HashSet<>();

    private Sounds() {
    }

    public static void spatialize(String namespace) {
        SPATIAL_NAMESPACES.add(namespace);
    }

    public static boolean spatializes(SoundInstance sound) {
        //? if >=26.1 {
        var namespace = sound.getIdentifier().getNamespace();
        //?} else {
        /*var namespace = sound.getLocation().getNamespace();
         *///?}
        return !sound.isRelative() && SPATIAL_NAMESPACES.contains(namespace);
    }

    public static void playDelayed(SoundInstance sound, int delay) {
        Minecraft.getInstance().getSoundManager().playDelayed(sound, delay);
    }

    /**
     * OpenAL source owned by the live native channel.
     */
    public static int source(Channel channel) {
        return channel.source;
    }

}
