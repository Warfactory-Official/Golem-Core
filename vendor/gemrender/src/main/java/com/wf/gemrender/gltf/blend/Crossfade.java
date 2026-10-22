package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.NodeTable;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Per-instance transition state: which clips are playing, since when, fading how. {@link #write} turns it
 * into override layers of an {@link AnimationBlend}; nothing else is stored, so every value is a function
 * of the {@code now} passed in (seconds on any monotonic clock the caller owns).
 *
 * <p>Weights, newest first: {@code w_k = remaining * curve(progress_k)}, the oldest entry takes what is
 * left; entries past full cover are dropped. An interrupted fade therefore continues from what is on
 * screen instead of popping.
 */
public final class Crossfade {
    /**
     * Step for the outgoing pose's velocity at an inertialized transition. Newest entry younger than one
     * step => forward difference: a backward probe predates it (looping entry wraps to its end).
     */
    public static final float VELOCITY_STEP_SECONDS = 1.0f / 120.0f;

    private final NodeTable table;
    private Entry[] entries = new Entry[4];
    private int count;

    @Nullable
    private Inertialization inertial;
    private float inertialStart;
    @Nullable
    private float[] sourceNow;
    @Nullable
    private float[] sourcePrev;
    @Nullable
    private float[] target;
    @Nullable
    private AnimationBlend probe;
    @Nullable
    private BlendEvaluator.Scratch probeScratch;

    public Crossfade(NodeTable table) {
        this.table = table;
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Entry();
        }
    }

    /**
     * {@code clip} looping from its start at {@code now}.
     */
    public void play(@Nullable GltfAnimation clip, float now, float fadeSeconds, FadeCurve curve) {
        play(clip, now, fadeSeconds, curve, 0.0f, 1.0f, true);
    }

    /**
     * {@code clipTime} at {@code now}, advancing at {@code speed} (0 = a held frame); {@code loop} false
     * holds the last frame.
     */
    public void play(@Nullable GltfAnimation clip, float now, float fadeSeconds, FadeCurve curve, float clipTime,
                     float speed, boolean loop) {
        if (curve == FadeCurve.INERTIAL && count > 0 && fadeSeconds > 0.0f) {
            inertialize(clip, now, fadeSeconds, clipTime, speed, loop);
            return;
        }
        if (count == entries.length) {
            int grown = entries.length;
            entries = Arrays.copyOf(entries, grown * 2);
            for (int i = grown; i < entries.length; i++) {
                entries[i] = new Entry();
            }
        }
        entries[count++].set(clip, now, clipTime, speed, loop, now, curve == FadeCurve.INERTIAL ? 0.0f
                : Math.max(0.0f, fadeSeconds), curve);
        if (fadeSeconds <= 0.0f) {
            keepNewest();
        }
    }

    private void inertialize(@Nullable GltfAnimation clip, float now, float fadeSeconds, float clipTime,
                             float speed, boolean loop) {
        if (inertial == null) {
            inertial = new Inertialization(table);
            sourceNow = table.newScratch();
            sourcePrev = table.newScratch();
            target = table.newScratch();
            probe = new AnimationBlend(entries.length + 1);
            probeScratch = new BlendEvaluator.Scratch();
        }
        float h = now - entries[count - 1].start < VELOCITY_STEP_SECONDS ? -VELOCITY_STEP_SECONDS
                : VELOCITY_STEP_SECONDS;
        sampleSource(now, sourceNow, true);
        sampleSource(now - h, sourcePrev, true);

        count = 0;
        entries[count++].set(clip, now, clipTime, speed, loop, now, 0.0f, FadeCurve.INERTIAL);
        sampleSource(now, target, false);

        inertial.begin(sourceNow, sourcePrev, target, h, fadeSeconds);
        inertialStart = now;
    }

    private void sampleSource(float at, float[] out, boolean withInertial) {
        probe.clear();
        emit(at, probe, 1.0f, BlendMask.ALL);
        if (withInertial && at >= inertialStart && inertial.activeAt(at - inertialStart)) {
            probe.inertialize(inertial, at - inertialStart, 1.0f, BlendMask.ALL);
        }
        probe.resolve();
        BlendEvaluator.evaluate(table, probe, out, probeScratch);
    }

    /**
     * Appends this frame's override layers, scaled by {@code weight} and masked.
     */
    public void write(float now, AnimationBlend blend, float weight, BlendMask mask) {
        prune(now);
        emit(now, blend, weight, mask);
        if (inertial != null && now >= inertialStart && inertial.activeAt(now - inertialStart)) {
            blend.inertialize(inertial, now - inertialStart, weight, mask);
        }
    }

    public void write(float now, AnimationBlend blend) {
        write(now, blend, 1.0f, BlendMask.ALL);
    }

    private void emit(float now, AnimationBlend blend, float weight, BlendMask mask) {
        float remaining = 1.0f;
        int first = blend.layerCount();
        for (int i = count - 1; i >= 0 && remaining > 0.0f; i--) {
            Entry entry = entries[i];
            float w = i == 0 ? remaining : remaining * entry.curve.weight(entry.progress(now));
            remaining -= w;
            if (w > 0.0f) {
                blend.override(entry.clip, entry.timeAt(now), weight * w, mask);
            }
        }
        if (blend.layerCount() == first && count == 0) {
            blend.override(null, 0.0f, weight, mask);
        }
    }

    private void prune(float now) {
        for (int i = count - 1; i > 0; i--) {
            Entry entry = entries[i];
            if (entry.progress(now) >= 1.0f) {
                for (int k = 0; k < i; k++) {
                    drop(0);
                }
                return;
            }
        }
    }

    private void keepNewest() {
        while (count > 1) {
            drop(0);
        }
    }

    private void drop(int index) {
        Entry gone = entries[index];
        System.arraycopy(entries, index + 1, entries, index, count - index - 1);
        entries[--count] = gone;
    }

    public boolean fading(float now) {
        return count > 1 && entries[count - 1].progress(now) < 1.0f
                || inertial != null && now >= inertialStart && inertial.activeAt(now - inertialStart);
    }

    @Nullable
    public GltfAnimation current() {
        return count == 0 ? null : entries[count - 1].clip;
    }

    public int playing() {
        return count;
    }

    private static final class Entry {
        @Nullable
        GltfAnimation clip;
        float start;
        float clipTime;
        float speed;
        boolean loop;
        float fadeStart;
        float fadeLength;
        FadeCurve curve = FadeCurve.LINEAR;

        void set(@Nullable GltfAnimation clip, float start, float clipTime, float speed, boolean loop,
                 float fadeStart, float fadeLength, FadeCurve curve) {
            this.clip = clip;
            this.start = start;
            this.clipTime = clipTime;
            this.speed = speed;
            this.loop = loop;
            this.fadeStart = fadeStart;
            this.fadeLength = fadeLength;
            this.curve = curve;
        }

        float progress(float now) {
            return fadeLength <= 0.0f ? 1.0f : (now - fadeStart) / fadeLength;
        }

        float timeAt(float now) {
            if (clip == null) {
                return 0.0f;
            }
            float t = clipTime + (now - start) * speed;
            return loop ? clip.loop(t) : Math.min(clip.duration(), Math.max(0.0f, t));
        }
    }
}
