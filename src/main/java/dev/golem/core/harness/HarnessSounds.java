package dev.golem.core.harness;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.SoundBuffer;
import dev.golem.core.client.Sounds;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

/**
 * Records every sound the client starts as a {@code [harness-sound]} line in the harness log, so a scenario can prove
 * which sound events played, where and how loud, on a client whose speakers nobody hears.
 *
 * <p>The line is written by a vanilla {@link SoundEventListener} (the subtitle overlay's hook). Below 26.1 vanilla
 * calls the listeners only after it skipped sounds whose category volume is zero, and the harness options mute every
 * category; {@code setOption("sound_capture", "true")} therefore raises every category except MASTER to 1.0 for the
 * scenario (master stays 0: the clients stay silent, and the master-volume skip comes after the listeners on every
 * node). {@code "false"} restores the previous volumes.
 *
 * <p>Line format: {@code [harness-sound] id=<event> source=<category> x=<x> y=<y> z=<z> volume=<v> pitch=<p>
 * file=<the resolved sound file>}.
 */
final class HarnessSounds {

    private static boolean installed;
    private static final Map<SoundSource, Double> saved = new EnumMap<>(SoundSource.class);

    private HarnessSounds() {}

    /** Adds the listener once the client's sound manager exists (called every client tick, cheap after the first). */
    static void tick(Minecraft mc) {
        if (installed || mc.getSoundManager() == null) return;
        installed = true;
        mc.getSoundManager().addListener(new Listener());
    }

    /** Implements {@code setOption("sound_capture", on)}; answers the previous state. */
    static String capture(Minecraft mc, boolean on) {
        String previous = String.valueOf(!saved.isEmpty());
        if (on && saved.isEmpty()) {
            for (SoundSource source : SoundSource.values()) {
                if (source == SoundSource.MASTER) continue;
                var option = mc.options.getSoundSourceOptionInstance(source);
                saved.put(source, option.get());
                option.set(1.0D);
            }
        } else if (!on && !saved.isEmpty()) {
            for (Map.Entry<SoundSource, Double> e : saved.entrySet()) {
                mc.options.getSoundSourceOptionInstance(e.getKey()).set(e.getValue());
            }
            saved.clear();
        }
        return previous;
    }

    /** A silent channel {@code distance} ahead of the listener, set up in vanilla's {@code play} order: its gain. */
    static JsonObject stereoFalloff(JsonObject req) {
        int channels = req.has("channels") ? req.get("channels").getAsInt() : 2;
        float distance = req.has("distance") ? req.get("distance").getAsFloat() : 0F;
        float max = req.has("max") ? req.get("max").getAsFloat() : 16F;
        float[] listener = new float[3];
        AL10.alGetListenerfv(AL10.AL_POSITION, listener);
        ByteBuffer pcm = MemoryUtil.memCalloc(4410 * 2 * channels);
        SoundBuffer buffer = new SoundBuffer(pcm, new AudioFormat(44100F, 16, channels, true, false));
        Channel channel = Channel.create();
        JsonObject out = new JsonObject();
        try {
            channel.setVolume(1F);
            channel.linearAttenuation(max);
            channel.setSelfPosition(new Vec3(listener[0], listener[1], listener[2] + distance));
            channel.setRelative(false);
            channel.attachStaticBuffer(buffer);
            channel.updateStream();
            int source = Sounds.source(channel);
            out.addProperty("channels", AL10.alGetBufferi(AL10.alGetSourcei(source, AL10.AL_BUFFER), AL10.AL_CHANNELS));
            out.addProperty("gain", AL10.alGetSourcef(source, AL10.AL_GAIN));
        } finally {
            channel.destroy();
            buffer.discardAlBuffer();
        }
        return out;
    }

    private static void record(SoundInstance sound) {
        try {
            String id;
            //? if >=26.1 {
            id = String.valueOf(sound.getIdentifier());
            //?} else {
            /*id = String.valueOf(sound.getLocation());
            *///?}
            float volume, pitch;
            try {
                volume = sound.getVolume();
                pitch = sound.getPitch();
            } catch (Throwable unresolved) {
                // getVolume/getPitch read the resolved Sound; the listener runs after resolve, so this is defensive only
                volume = Float.NaN;
                pitch = Float.NaN;
            }
            String file = sound.getSound() == null ? "none" : String.valueOf(sound.getSound().getLocation());
            HarnessLog.add(String.format(Locale.ROOT,
                    "[harness-sound] id=%s source=%s x=%.2f y=%.2f z=%.2f volume=%.3f pitch=%.3f file=%s",
                    id, sound.getSource().getName(), sound.getX(), sound.getY(), sound.getZ(), volume, pitch, file));
        } catch (Throwable t) {
            HarnessLog.add("[harness-sound] unreadable sound: " + t);
        }
    }

    private static final class Listener implements SoundEventListener {
        //? if >=1.21 {
        @Override
        public void onPlaySound(SoundInstance sound, WeighedSoundEvents accessor, float range) {
            record(sound);
        }
        //?} else {
        /*@Override
        public void onPlaySound(SoundInstance sound, WeighedSoundEvents accessor) {
            record(sound);
        }
        *///?}
    }
}
