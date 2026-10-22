package com.wf.gemrender.gltf.blend;

import com.wf.gemrender.gltf.GltfAnimation;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * An {@link AnimationBlend} quantized for sharing: time per layer to {@code timeQuantum}, weight to
 * {@code 1/weightSteps}. Two blends with equal keys evaluate to the same pose, because {@link #load}
 * rebuilds the representative (quantized) blend that is actually evaluated.
 *
 * <p>Mutable so a lookup allocates nothing: {@link #set} a thread's probe, {@link #copy} only on a miss.
 */
public final class BlendKey {
    private Object owner;
    private int lod;
    private float timeQuantum;
    private int weightSteps;
    private int layers;
    private GltfAnimation[] clips = new GltfAnimation[4];
    private int[] buckets = new int[4];
    private int[] weights = new int[4];
    private BlendMask[] masks = new BlendMask[4];
    private BlendMode[] modes = new BlendMode[4];
    private AdditiveReference[] references = new AdditiveReference[4];
    @Nullable
    private Inertialization inertial;
    private int inertialGeneration;
    private int inertialBucket;
    private int inertialWeight;
    private BlendMask inertialMask;
    private int hash;

    /**
     * Resolves {@code blend}'s sync groups as a side effect.
     */
    public BlendKey set(Object owner, int lod, AnimationBlend blend, float timeQuantum, int weightSteps) {
        blend.resolve();
        this.owner = owner;
        this.lod = lod;
        this.timeQuantum = timeQuantum;
        this.weightSteps = weightSteps;
        int n = blend.layerCount();
        ensure(n);
        int h = System.identityHashCode(owner) * 31 + lod;
        int i = 0;
        for (int layer = 0; layer < n; layer++) {
            int weight = Math.round(blend.weight(layer) * weightSteps);
            if (weight == 0) {
                continue;
            }
            clips[i] = blend.clip(layer);
            buckets[i] = bucket(blend.resolvedTime(layer), timeQuantum);
            weights[i] = weight;
            masks[i] = blend.mask(layer);
            modes[i] = blend.mode(layer);
            references[i] = blend.reference(layer);
            h = ((((h * 31 + Objects.hashCode(clips[i])) * 31 + buckets[i]) * 31 + weights[i]) * 31
                    + masks[i].hashCode()) * 31 + modes[i].ordinal();
            h = h * 31 + Objects.hashCode(references[i]);
            i++;
        }
        layers = i;
        inertial = blend.inertial();
        if (inertial != null) {
            inertialGeneration = inertial.generation();
            inertialBucket = bucket(blend.inertialElapsed(), timeQuantum);
            inertialWeight = Math.round(blend.inertialWeight() * weightSteps);
            inertialMask = blend.inertialMask();
            h = (((h * 31 + System.identityHashCode(inertial)) * 31 + inertialGeneration) * 31 + inertialBucket)
                    * 31 + inertialWeight;
        } else {
            inertialMask = null;
        }
        hash = h;
        return this;
    }

    private static int bucket(float time, float quantum) {
        if (quantum <= 0.0f) {
            return Float.floatToIntBits(time == 0.0f ? 0.0f : time);
        }
        return Math.round(time / quantum);
    }

    private float representative(int bucket) {
        return timeQuantum <= 0.0f ? Float.intBitsToFloat(bucket) : bucket * timeQuantum;
    }

    private void ensure(int n) {
        if (clips.length >= n) {
            return;
        }
        clips = Arrays.copyOf(clips, n);
        buckets = Arrays.copyOf(buckets, n);
        weights = Arrays.copyOf(weights, n);
        masks = Arrays.copyOf(masks, n);
        modes = Arrays.copyOf(modes, n);
        references = Arrays.copyOf(references, n);
    }

    /**
     * The blend this key stands for, as evaluated; resolved.
     */
    public void load(AnimationBlend into) {
        into.clear();
        for (int i = 0; i < layers; i++) {
            float weight = weights[i] / (float) weightSteps;
            if (modes[i] == BlendMode.OVERRIDE) {
                into.override(clips[i], representative(buckets[i]), weight, masks[i]);
            } else {
                into.additive(clips[i], representative(buckets[i]), weight, masks[i], references[i]);
            }
        }
        if (inertial != null) {
            into.inertialize(inertial, representative(inertialBucket), inertialWeight / (float) weightSteps,
                    inertialMask);
        }
        into.resolve();
    }

    /**
     * One full-weight unmasked override layer, zero-weight layers dropped, no inertialization: the
     * single-clip key, so callers can share with the single-clip API.
     */
    public boolean isSingleClip() {
        return layers == 1 && inertial == null && modes[0] == BlendMode.OVERRIDE && weights[0] == weightSteps
                && masks[0].isAll();
    }

    /**
     * Carries a {@link Crossfade}'s inertialization: per instance, never equal to another instance's key.
     */
    public boolean isPrivate() {
        return inertial != null;
    }

    public boolean isRest() {
        return layers == 0 && inertial == null;
    }

    @Nullable
    public GltfAnimation clip(int layer) {
        return clips[layer];
    }

    /**
     * Quantized (representative) time of a layer.
     */
    public float time(int layer) {
        return representative(buckets[layer]);
    }

    public BlendKey copy() {
        BlendKey c = new BlendKey();
        c.owner = owner;
        c.lod = lod;
        c.timeQuantum = timeQuantum;
        c.weightSteps = weightSteps;
        c.layers = layers;
        c.clips = Arrays.copyOf(clips, layers);
        c.buckets = Arrays.copyOf(buckets, layers);
        c.weights = Arrays.copyOf(weights, layers);
        c.masks = Arrays.copyOf(masks, layers);
        c.modes = Arrays.copyOf(modes, layers);
        c.references = Arrays.copyOf(references, layers);
        c.inertial = inertial;
        c.inertialGeneration = inertialGeneration;
        c.inertialBucket = inertialBucket;
        c.inertialWeight = inertialWeight;
        c.inertialMask = inertialMask;
        c.hash = hash;
        return c;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BlendKey that) || hash != that.hash || owner != that.owner || lod != that.lod
                || layers != that.layers || weightSteps != that.weightSteps
                || Float.compare(timeQuantum, that.timeQuantum) != 0 || inertial != that.inertial) {
            return false;
        }
        for (int i = 0; i < layers; i++) {
            if (buckets[i] != that.buckets[i] || weights[i] != that.weights[i] || modes[i] != that.modes[i]
                    || !Objects.equals(clips[i], that.clips[i]) || !masks[i].equals(that.masks[i])
                    || !Objects.equals(references[i], that.references[i])) {
                return false;
            }
        }
        return inertial == null || inertialGeneration == that.inertialGeneration
                && inertialBucket == that.inertialBucket && inertialWeight == that.inertialWeight
                && inertialMask.equals(that.inertialMask);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
