package dev.golem.core.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

//? if >=26.1 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Spawning a mod-authored particle, and looking up the sprite it draws with.
 *
 * <p>A mod that registers its particles as vanilla {@code ParticleType}s does not need this — it can
 * call {@code level.addParticle(options, ...)}. This is for the other model, which HBM uses: the
 * server sends one packet naming an <i>effect</i> plus an arbitrary NBT payload, and the client
 * constructs however many particles that effect implies. Vanilla {@code ParticleOptions} cannot carry
 * that payload without one registered type per effect, so those particles are built directly and
 * handed to the engine.
 *
 * <p>The sprite lookup is the part that actually moved between eras: 26.1 routes every atlas through
 * {@code AtlasManager} keyed by a {@code SpriteId}, where older versions exposed
 * {@code Minecraft.getTextureAtlas(atlasTexture)} as a plain sprite function.
 *
 * <p>Client-only.
 */
public final class Particles {

    private Particles() {}

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
     * Resolves a sprite on the <b>particles</b> atlas.
     *
     * <p>No atlas-extension file is needed: vanilla's particles atlas is declared with a
     * {@code directory} source over {@code textures/particle}, and directory sources scan <i>every</i>
     * namespace, so a mod's own {@code textures/particle/} folder is stitched in automatically.
     *
     * <p><b>But mind the prefix.</b> That source is declared with {@code "prefix": ""}, where
     * {@code blocks.json} uses {@code "prefix": "block/"} — so the sprite id carries <i>no</i>
     * {@code particle/} segment: {@code assets/hbm/textures/particle/particle_base.png} is
     * {@code hbm:particle_base}, not {@code hbm:particle/particle_base}.
     *
     * <p>An id that is not on the atlas does <b>not</b> throw — the atlas returns its missing sprite
     * and the particle draws as a checkerboard. Only a wrong <i>atlas</i> id throws. The two mistakes
     * therefore look nothing alike, which is worth remembering when one of them bites.
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
        /*return Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES)
                .apply(texture);
        *///?}
    }
}
