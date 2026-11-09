package dev.golem.core.client;

import org.lwjgl.openal.AL10;

/** 1.7's software linear distance gain, for stereo sources, which OpenAL never attenuates. Sound thread only. */
public final class StereoFalloff {

    private StereoFalloff() {}

    public static boolean stereo(int source) {
        int buffer = AL10.alGetSourcei(source, AL10.AL_BUFFER);
        return buffer != 0 && AL10.alGetBufferi(buffer, AL10.AL_CHANNELS) > 1;
    }

    /** The source's gain: {@code base} scaled by {@code 1 - distance / maxDistance}, or {@code base} if unattenuated. */
    public static float gain(int source, float base, float maxDistance) {
        if (maxDistance <= 0F) return base;
        float[] pos = new float[3];
        AL10.alGetSourcefv(source, AL10.AL_POSITION, pos);
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) != AL10.AL_TRUE) {
            float[] listener = new float[3];
            AL10.alGetListenerfv(AL10.AL_POSITION, listener);
            pos[0] -= listener[0];
            pos[1] -= listener[1];
            pos[2] -= listener[2];
        }
        float distance = (float) Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        return base * Math.max(0F, 1F - distance / maxDistance);
    }
}
