package dev.golem.core.render;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
 *///?}

/**
 * Particle factories and sprite lookups use the 26.1 contract across client backends.
 */
public final class Particles {

    public enum Quality {
        ALL,
        DECREASED,
        MINIMAL
    }

    private Particles() {
    }

    /**
     * Particle factories receive the engine's random source through one signature.
     */
    public abstract static class Provider<T extends ParticleOptions> implements ParticleProvider<T> {
        protected abstract Particle create(T options, ClientLevel level, double x, double y, double z,
                                           double motionX, double motionY, double motionZ, RandomSource random);

        //? if >=26.1 {
        @Override
        public final Particle createParticle(T options, ClientLevel level, double x, double y, double z,
                                             double motionX, double motionY, double motionZ, RandomSource random) {
            return create(options, level, x, y, z, motionX, motionY, motionZ, random);
        }
        //?} else {
        /*@Override
        public final Particle createParticle(T options, ClientLevel level, double x, double y, double z,
                                             double motionX, double motionY, double motionZ) {
            return create(options, level, x, y, z, motionX, motionY, motionZ, level.getRandom());
        }
        *///?}
    }

    public static Vec3 position(Particle particle) {
        return new Vec3(particle.x, particle.y, particle.z);
    }

    /**
     * Visits pending then active particles on the client thread; the action must not alter their collections.
     */
    public static void forEach(Minecraft client, Consumer<Particle> action) {
        var engine = client.particleEngine;
        engine.particlesToAdd.forEach(action);
        for (var group : engine.particles.values()) {
            //? if >=26.1 {
            group.getAll().forEach(action);
            //?} else {
            /*group.forEach(action);
             *///?}
        }
    }

    /**
     * Adds an already-constructed particle to the client particle engine.
     *
     * <p>Safe to call with a null particle (a creator that decided not to emit); safe to call before
     * a level exists, where it is a no-op rather than a crash.
     */
    public static void add(Particle particle) {
        if (particle == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        minecraft.particleEngine.add(particle);
    }

    /**
     * Applies a particle tint across the particle-class hierarchy used by each era.
     */
    public static void color(Particle particle, float red, float green, float blue) {
        //? if >=26.1 {
        if (particle instanceof net.minecraft.client.particle.SingleQuadParticle quad) {
            quad.setColor(red, green, blue);
        }
        //?} else {
        /*particle.setColor(red, green, blue);
         *///?}
    }

    /**
     * The user's particle-density setting, whose vanilla enum moved packages in 26.1.
     */
    public static Quality quality() {
        //? if >=26.1 {
        net.minecraft.server.level.ParticleStatus status = Minecraft.getInstance().options.particles().get();
        //?} else {
        /*net.minecraft.client.ParticleStatus status = Minecraft.getInstance().options.particles().get();
         *///?}
        return switch (status) {
            case ALL -> Quality.ALL;
            case DECREASED -> Quality.DECREASED;
            case MINIMAL -> Quality.MINIMAL;
        };
    }

    /**
     * Resolves a sprite on the <b>particles</b> atlas.
     *
     * <p>No atlas-extension file is needed: vanilla's particles atlas has a {@code directory} source over
     * {@code textures/particle} that scans <i>every</i> namespace, so a mod's own
     * {@code textures/particle/} folder is stitched in.
     *
     * <p><b>Mind the prefix.</b> That source has {@code "prefix": ""} (where {@code blocks.json} uses
     * {@code "prefix": "block/"}), so the sprite id carries <i>no</i> {@code particle/} segment:
     * {@code assets/hbm/textures/particle/particle_base.png} is {@code hbm:particle_base}.
     *
     * <p>An id that is not on the atlas does <b>not</b> throw; the atlas returns its missing sprite and the
     * particle draws as a checkerboard. Only a wrong <i>atlas</i> id throws.
     */
    public static TextureAtlasSprite sprite(
            //? if >=26.1 {
            Identifier
                    //?} else {
                    /*ResourceLocation
                     *///?}
                    texture) {
        //? if >=26.1 {
        // The SpriteId's first field is the atlas TEXTURE location
        // (minecraft:textures/atlas/particles.png), not the atlas definition id (minecraft:particles).
        // AtlasManager keeps two maps and get() consults the by-texture one; handing it the definition
        // id throws "Invalid atlas texture id". getAtlasOrThrow is the method that takes the other one.
        return Minecraft.getInstance()
                        .getAtlasManager()
                        .get(new net.minecraft.client.resources.model.sprite.SpriteId(
                                net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES, texture));
        //?} else {
        /*return ((net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance()
                .getTextureManager()
                .getTexture(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES))
                .getSprite(texture);
        *///?}
    }

    /**
     * Creates an explicitly requested particle without a second density or distance lottery.
     */
    public static Particle spawn(ParticleOptions type, double x, double y, double z,
                                 double motionX, double motionY, double motionZ) {
        return Minecraft.getInstance().particleEngine.createParticle(type, x, y, z, motionX, motionY, motionZ);
    }

    /**
     * Spawns a coloured vanilla dust particle, hiding its constructor's colour-shape change.
     */
    public static void dust(Level level, double x, double y, double z,
                            float red, float green, float blue, float size, double motionX, double motionY,
                            double motionZ) {
        int color = ((int) (Math.max(0F, Math.min(1F, red)) * 255F) << 16)
                | ((int) (Math.max(0F, Math.min(1F, green)) * 255F) << 8)
                | (int) (Math.max(0F, Math.min(1F, blue)) * 255F);
        //? if >=26.1 {
        var particle = spawn(new net.minecraft.core.particles.DustParticleOptions(color, size),
                x, y, z, motionX, motionY, motionZ);
        //?} else {
        /*var particle = spawn(new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(red, green, blue), size), x, y, z, motionX, motionY, motionZ);
        *///?}
        particle.setParticleSpeed(motionX, motionY, motionZ);
    }
}
