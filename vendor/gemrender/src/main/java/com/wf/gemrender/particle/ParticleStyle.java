package com.wf.gemrender.particle;

public final class ParticleStyle {
    public static final int FLOATS = 24;

    public static final float FULL_BRIGHT = 240.0f / 256.0f;

    public final float drag;
    public final float dragY;
    public final float gravity;
    public final float sizeAtBirth;
    public final float sizeGrowth;
    public final float tintRed;
    public final float tintGreen;
    public final float tintBlue;
    public final float alphaScale;
    public final float alphaFalloff;
    public final float coolFloor;
    public final float coolSpan;
    public final float spinRate;
    public final float lightBlock;
    public final float lightSky;
    public final float fadeIn;
    public final float restitution;
    public final float friction;
    public final ContactResponse response;
    public final float fadeOut;
    public final float streak;
    public final float glow;

    private final float[] data;

    private ParticleStyle(float[] data) {
        this.data = data;
        drag = data[0];
        gravity = data[1];
        sizeAtBirth = data[2];
        sizeGrowth = data[3];
        tintRed = data[4];
        tintGreen = data[5];
        tintBlue = data[6];
        alphaScale = data[7];
        alphaFalloff = data[8];
        coolFloor = data[9];
        coolSpan = data[10];
        spinRate = data[11];
        lightBlock = data[12];
        lightSky = data[13];
        dragY = data[14];
        fadeIn = data[15];
        restitution = data[16];
        friction = data[17];
        ContactResponse[] responses = ContactResponse.values();
        response = responses[Math.max(0, Math.min(responses.length - 1, (int) data[18]))];
        fadeOut = data[19];
        streak = data[20];
        glow = data[21];
    }

    /** Whether a spawn through this style is worth sweeping for a contact at all. */
    public boolean collides() {
        return response != ContactResponse.NONE;
    }

    public static ParticleStyle of(float... floats) {
        if (floats.length != FLOATS) {
            throw new IllegalArgumentException("A style is " + FLOATS + " floats, got " + floats.length);
        }
        return new ParticleStyle(floats.clone());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static float dragFromPerTickFactor(float factor) {
        if (factor <= 0.0f || factor >= 1.0f) {
            return 0.0f;
        }
        return (float) (-20.0 * Math.log(factor));
    }

    public static float gravityFromPerTickDelta(float deltaPerTick) {
        return -deltaPerTick * 400.0f;
    }

    public void write(float[] target, int offset) {
        System.arraycopy(data, 0, target, offset, FLOATS);
    }

    public static final class Builder {
        private float drag = 0.0f;
        private float dragY = 0.0f;
        private boolean dragYSet;
        private float gravity = 0.0f;
        private float fadeIn = 0.0f;
        private float sizeAtBirth = 1.0f;
        private float sizeGrowth = 0.0f;
        private float tintRed = 1.0f;
        private float tintGreen = 1.0f;
        private float tintBlue = 1.0f;
        private float alphaScale = 1.0f;
        private float alphaFalloff = 1.0f;
        private float coolFloor = 1.0f;
        private float coolSpan = 1.0f;
        private float spinRate = 0.0f;
        private float lightBlock = FULL_BRIGHT;
        private float lightSky = FULL_BRIGHT;
        private ContactResponse response = ContactResponse.NONE;
        private float restitution = 0.0f;
        private float friction = 0.0f;
        private float fadeOut = 0.0f;
        private float streak = 0.0f;
        private float glow = 0.0f;

        private Builder() {
        }

        public Builder drag(float perSecond) {
            drag = perSecond;
            return this;
        }

        public Builder drag(float horizontalPerSecond, float verticalPerSecond) {
            drag = horizontalPerSecond;
            dragY = verticalPerSecond;
            dragYSet = true;
            return this;
        }

        public Builder fadeIn(float unitAge) {
            fadeIn = unitAge;
            return this;
        }

        /**
         * Hold {@code alphaScale} until this unit age, then run the {@code alpha} falloff over the rest of the
         * life. 0 fades over the whole life. {@link GemRenderParticleTypes#BODY} shrinks through the window
         * instead.
         */
        public Builder fadeOut(float unitAge) {
            if (!(unitAge >= 0.0f && unitAge < 1.0f)) {
                throw new IllegalArgumentException("fadeOut is a unit age in [0, 1), got " + unitAge);
            }
            fadeOut = unitAge;
            return this;
        }

        /**
         * {@link GemRenderParticleTypes#STREAK} length: the distance covered in this many seconds at the
         * particle's current speed, never shorter than its width.
         */
        public Builder streak(float seconds) {
            streak = seconds;
            return this;
        }

        /**
         * Share of alpha drawn as emission while hot (fading over the {@link #cool} span when one is set);
         * the rest absorbs. Read only by {@link ParticleModels#glowing}; other materials ignore it.
         */
        public Builder glow(float share) {
            if (!(share >= 0.0f && share <= 1.0f)) {
                throw new IllegalArgumentException("glow is a share in [0, 1], got " + share);
            }
            glow = share;
            return this;
        }

        public Builder gravity(float blocksPerSecondSquared) {
            gravity = blocksPerSecondSquared;
            return this;
        }

        public Builder size(float atBirth, float growth) {
            sizeAtBirth = atBirth;
            sizeGrowth = growth;
            return this;
        }

        public Builder tint(float red, float green, float blue) {
            tintRed = red;
            tintGreen = green;
            tintBlue = blue;
            return this;
        }

        public Builder tint(int rgb) {
            return tint(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
        }

        public Builder alpha(float scale, float falloff) {
            alphaScale = scale;
            alphaFalloff = falloff;
            return this;
        }

        public Builder cool(float floor, float span) {
            coolFloor = floor;
            coolSpan = span;
            return this;
        }

        public Builder spin(float radiansPerSecond) {
            spinRate = radiansPerSecond;
            return this;
        }

        public Builder light(float block, float sky) {
            lightBlock = block;
            lightSky = sky;
            return this;
        }

        /**
         * Come to rest on the first block hit, and hold the attitude of the impact.
         */
        public Builder stopsOnContact() {
            response = ContactResponse.STOP;
            restitution = 0.0f;
            friction = 0.0f;
            return this;
        }

        /**
         * Rebound once and settle where that rebound lands.
         *
         * @param restitution fraction of the velocity <em>into</em> the surface that comes back out; 0 is a
         *                    dead stop and 1 is a perfect rebound
         * @param friction    fraction of the velocity <em>along</em> the surface that survives; 1 slides
         *                    freely, 0 kills the skid
         */
        public Builder bouncesOnContact(float restitution, float friction) {
            response = ContactResponse.BOUNCE;
            this.restitution = Math.max(0.0f, Math.min(1.0f, restitution));
            this.friction = Math.max(0.0f, Math.min(1.0f, friction));
            return this;
        }

        /** Vanish on the first block hit. */
        public Builder diesOnContact() {
            response = ContactResponse.DIE;
            restitution = 0.0f;
            friction = 0.0f;
            return this;
        }

        public ParticleStyle build() {
            return ParticleStyle.of(drag, gravity, sizeAtBirth, sizeGrowth,
                    tintRed, tintGreen, tintBlue, alphaScale,
                    alphaFalloff, coolFloor, coolSpan, spinRate,
                    lightBlock, lightSky, dragYSet ? dragY : drag, fadeIn,
                    restitution, friction, response.ordinal(), fadeOut,
                    streak, glow, 0.0f, 0.0f);
        }
    }
}
