package com.wf.gemrender.volume;

public final class Volume {
    private final int slot;

    private VolumeStyle style;

    private float extentX = 1.0f;

    private float extentY = 1.0f;

    private float extentZ = 1.0f;

    private float fade = 1.0f;

    private float seed;

    private VolumeField field;

    private boolean closed;

    private boolean hidden;

    private Volume(int slot, VolumeStyle style) {
        this.slot = slot;
        this.style = style;
    }

    public static Volume create(VolumeStyle style) {
        Volume volume = new Volume(VolumeBuffer.getInstance()
                .allocate(), style);
        volume.flush();
        return volume;
    }

    public int slot() {
        return slot;
    }

    public Volume style(VolumeStyle value) {
        style = value;
        flush();
        return this;
    }

    public Volume extent(float x, float y, float z) {
        extentX = x;
        extentY = y;
        extentZ = z;
        flush();
        return this;
    }

    public Volume fade(float value) {
        fade = value;
        flush();
        return this;
    }

    public Volume seed(float value) {
        seed = value;
        flush();
        return this;
    }

    public Volume field(VolumeField value) {
        field = value;
        flush();
        return this;
    }

    public VolumeField field() {
        return field;
    }

    public VolumeStyle style() {
        return style;
    }

    public float fade() {
        return fade;
    }

    public float minExtent() {
        return Math.min(extentX, Math.min(extentY, extentZ));
    }

    /** Hidden: drawn with fade 0, {@link #fade()} kept ({@link com.wf.gemrender.medium.CameraMedium}). */
    public Volume hidden(boolean value) {
        if (hidden != value) {
            hidden = value;
            flush();
        }
        return this;
    }

    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        if (field != null) {
            field.close();
            field = null;
        }
        VolumeBuffer.getInstance()
                .release(slot);
    }

    private void flush() {
        if (closed) {
            return;
        }

        VolumeAtlas atlas = VolumeAtlas.getInstance();
        VolumeField current = field;

        VolumeBuffer.getInstance()
                .write(slot, style, extentX, extentY, extentZ, hidden ? 0.0f : fade, seed,
                        current == null ? 0.0f : atlas.originU(current.tile()),
                        current == null ? 0.0f : atlas.originV(current.tile()),
                        current == null ? 0.0f : atlas.originW(current.tile()),
                        current == null ? 0.0f : atlas.scale());
    }
}
