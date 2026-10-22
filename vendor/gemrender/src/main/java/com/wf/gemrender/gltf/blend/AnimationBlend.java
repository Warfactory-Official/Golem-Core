package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * One instance's layer stack for one frame: refill it every frame, hand it to
 * {@link com.wf.gemrender.render.PoseCache#pose(com.wf.gemrender.gltf.GltfPaletteLayout,
 * com.wf.gemrender.gltf.skin.SkinnedBounds, com.wf.gemrender.gltf.morph.GltfMorphLayout, AnimationBlend, int)}
 * or {@link BlendEvaluator}.
 *
 * <p>Per instance, single thread, no allocation once it has grown to the frame's layer count.
 * Layers are applied in order: override layers form one normalized weighted blend per node, additive
 * layers follow, then a {@link Crossfade}'s inertialization offset.
 */
public final class AnimationBlend {
    public static final int NO_SYNC = -1;

    private GltfAnimation[] clips;
    private float[] times;
    private float[] weights;
    private BlendMask[] masks;
    private BlendMode[] modes;
    private AdditiveReference[] references;
    private int[] syncGroups;
    private float[] resolved;
    private int count;

    @Nullable
    private Inertialization inertial;
    private float inertialElapsed;
    private BlendMask inertialMask = BlendMask.ALL;
    private float inertialWeight;

    public AnimationBlend() {
        this(4);
    }

    public AnimationBlend(int capacity) {
        int n = Math.max(1, capacity);
        clips = new GltfAnimation[n];
        times = new float[n];
        weights = new float[n];
        masks = new BlendMask[n];
        modes = new BlendMode[n];
        references = new AdditiveReference[n];
        syncGroups = new int[n];
        resolved = new float[n];
    }

    public AnimationBlend clear() {
        Arrays.fill(clips, 0, count, null);
        Arrays.fill(references, 0, count, null);
        count = 0;
        inertial = null;
        return this;
    }

    /**
     * {@code clip == null} contributes the rest pose.
     *
     * @return the layer index
     */
    public int override(@Nullable GltfAnimation clip, float timeSeconds, float weight) {
        return add(clip, timeSeconds, weight, BlendMask.ALL, BlendMode.OVERRIDE, null);
    }

    public int override(@Nullable GltfAnimation clip, float timeSeconds, float weight, BlendMask mask) {
        return add(clip, timeSeconds, weight, mask, BlendMode.OVERRIDE, null);
    }

    /**
     * {@code weight} may exceed 1 (exaggeration).
     */
    public int additive(GltfAnimation clip, float timeSeconds, float weight, BlendMask mask,
                        AdditiveReference reference) {
        if (clip == null || reference == null) {
            throw new IllegalArgumentException("an additive layer needs a clip and a reference");
        }
        return add(clip, timeSeconds, weight, mask, BlendMode.ADDITIVE, reference);
    }

    /**
     * Layers sharing {@code group} run at the phase fraction of the heaviest of them, each over its own
     * duration (a walk and a run of different lengths stay in step).
     */
    public AnimationBlend sync(int layer, int group) {
        check(layer);
        syncGroups[layer] = group;
        return this;
    }

    private int add(@Nullable GltfAnimation clip, float time, float weight, BlendMask mask, BlendMode mode,
                    @Nullable AdditiveReference reference) {
        if (!(weight >= 0.0f) || Float.isInfinite(weight) || Float.isNaN(time) || Float.isInfinite(time)) {
            throw new IllegalArgumentException("layer weight " + weight + " at time " + time);
        }
        if (count == clips.length) {
            grow();
        }
        int i = count++;
        clips[i] = clip;
        times[i] = time;
        weights[i] = mode == BlendMode.OVERRIDE ? Math.min(1.0f, weight) : weight;
        masks[i] = mask == null ? BlendMask.ALL : mask;
        modes[i] = mode;
        references[i] = reference;
        syncGroups[i] = NO_SYNC;
        return i;
    }

    private void grow() {
        int n = clips.length * 2;
        clips = Arrays.copyOf(clips, n);
        times = Arrays.copyOf(times, n);
        weights = Arrays.copyOf(weights, n);
        masks = Arrays.copyOf(masks, n);
        modes = Arrays.copyOf(modes, n);
        references = Arrays.copyOf(references, n);
        syncGroups = Arrays.copyOf(syncGroups, n);
        resolved = Arrays.copyOf(resolved, n);
    }

    void inertialize(Inertialization inertialization, float elapsedSeconds, float weight, BlendMask mask) {
        inertial = inertialization;
        inertialElapsed = elapsedSeconds;
        inertialWeight = weight;
        inertialMask = mask;
    }

    /**
     * Sync groups applied into {@link #resolvedTime}.
     */
    public void resolve() {
        for (int i = 0; i < count; i++) {
            resolved[i] = times[i];
        }
        for (int i = 0; i < count; i++) {
            int group = syncGroups[i];
            if (group == NO_SYNC || !isLeader(i, group)) {
                continue;
            }
            float leaderDuration = duration(i);
            if (leaderDuration <= 0.0f) {
                continue;
            }
            float phase = clips[i].loop(times[i]) / leaderDuration;
            for (int j = 0; j < count; j++) {
                if (j != i && syncGroups[j] == group && duration(j) > 0.0f) {
                    resolved[j] = phase * duration(j);
                }
            }
        }
    }

    private boolean isLeader(int layer, int group) {
        for (int j = 0; j < count; j++) {
            if (syncGroups[j] == group && (weights[j] > weights[layer] || weights[j] == weights[layer] && j < layer)) {
                return false;
            }
        }
        return true;
    }

    private float duration(int layer) {
        return clips[layer] == null ? 0.0f : clips[layer].duration();
    }

    private void check(int layer) {
        if (layer < 0 || layer >= count) {
            throw new IndexOutOfBoundsException("layer " + layer + " of " + count);
        }
    }

    public int layerCount() {
        return count;
    }

    @Nullable
    public GltfAnimation clip(int layer) {
        return clips[layer];
    }

    public float time(int layer) {
        return times[layer];
    }

    public float resolvedTime(int layer) {
        return resolved[layer];
    }

    public float weight(int layer) {
        return weights[layer];
    }

    public BlendMask mask(int layer) {
        return masks[layer];
    }

    public BlendMode mode(int layer) {
        return modes[layer];
    }

    @Nullable
    public AdditiveReference reference(int layer) {
        return references[layer];
    }

    public int syncGroup(int layer) {
        return syncGroups[layer];
    }

    @Nullable
    Inertialization inertial() {
        return inertial;
    }

    float inertialElapsed() {
        return inertialElapsed;
    }

    float inertialWeight() {
        return inertialWeight;
    }

    BlendMask inertialMask() {
        return inertialMask;
    }

    /**
     * True when a per-instance inertialization offset rides on this frame; such a pose is never shared.
     */
    public boolean isPrivate() {
        return inertial != null;
    }

    /**
     * Some layer samples a clip, or an inertialization rides: the pose can change over time.
     */
    public boolean moves() {
        if (inertial != null) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            if (clips[i] != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exactly one full-weight, unmasked override layer and nothing else: the legacy single-clip pose.
     */
    public boolean isSingleClip() {
        return count == 1 && inertial == null && modes[0] == BlendMode.OVERRIDE && weights[0] == 1.0f
                && masks[0].isAll();
    }
}
