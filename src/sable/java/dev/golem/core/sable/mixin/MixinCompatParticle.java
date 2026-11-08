package dev.golem.core.sable.mixin;

import dev.golem.core.render.CompatParticle;
import dev.ryanhcode.sable.api.particle.ParticleSubLevelKickable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets a mod particle that does not collide ({@code hasPhysics} off, 1.7's {@code noClip}) pass through sub-level blocks.
 * Sable collides every particle with the blocks of the ships around it, physics or not, so a puff born inside a ship's
 * block (a cooling tower's spout) is held there for its whole life.
 *
 * <p>Why a mixin (AGENTS.md section 7). Neither vanilla nor the loaders have a hook, as Sable replaces
 * {@code Particle.move} itself. Sable's API for it, {@link ParticleSubLevelKickable}, must be implemented by the particle
 * class, and {@link CompatParticle} is shared by every node while Sable exists on two, so it cannot name the interface.
 * Access widening adds no interface. This mixin only adds the interface, with no injection; the config applies it only
 * when Sable is loaded.
 */
@Mixin(value = CompatParticle.class, remap = false)
public abstract class MixinCompatParticle extends Particle implements ParticleSubLevelKickable {

    private MixinCompatParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Override
    public boolean sable$shouldCareAboutIntersectingSubLevels() {
        return this.hasPhysics;
    }

    @Override
    public boolean sable$shouldKickFromTracking() {
        return true;
    }

    @Override
    public boolean sable$shouldCollideWithTrackingSubLevel() {
        return this.hasPhysics;
    }
}
