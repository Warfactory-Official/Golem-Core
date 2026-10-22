package com.wf.gemrender.gltf.blend;

public enum FadeCurve {
    LINEAR,
    /**
     * {@code 3f^2 - 2f^3}: zero weight velocity at both ends.
     */
    SMOOTH,
    /**
     * Inertialization (Bollo, GDC 2018): the outgoing pose is dropped at once and the gap to the incoming
     * one decays by a quintic from the outgoing pose's own velocity. One clip evaluated instead of two;
     * the decaying offset is per instance, so such a pose is never shared.
     */
    INERTIAL;

    public float weight(float progress) {
        float f = Math.min(1.0f, Math.max(0.0f, progress));
        return this == SMOOTH ? f * f * (3.0f - 2.0f * f) : f;
    }
}
