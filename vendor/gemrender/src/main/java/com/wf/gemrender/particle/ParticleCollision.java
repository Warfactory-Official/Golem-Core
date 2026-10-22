package com.wf.gemrender.particle;

import org.joml.Vector3f;

/**
 * Where a particle's flight runs into the world, worked out once at spawn.
 *
 * <p>A particle has no state to read, so it cannot discover a wall while it flies. What it can do is be told
 * in advance: the trajectory is a closed form, so the whole arc is knowable the moment the particle is born,
 * and walking it against the world costs one sweep at spawn instead of a test per particle per tick. What
 * comes out is a {@link Contact} (two ages and a face), and from then on the shader is still evaluating a
 * closed form, still writing nothing per frame.
 *
 * <p>The sweep must agree with what the GPU draws, so it walks the arc through {@link ParticleMotion}, which
 * is the same closed form {@code particle.glsl} evaluates. Predicting through a second, tidier model would
 * put contacts where the particle visibly is not.
 *
 * <p>Nothing here knows about Minecraft: the world arrives as a {@link Probe}, which is what makes the
 * prediction testable against a floor nobody had to load a level to build. {@link LevelContactProbe} is the
 * real one.
 */
public final class ParticleCollision {

    /**
     * An age no particle reaches. It is how "this one never hits anything" is spelled in the buffer, and it
     * matters that it is a number rather than a flag: the shader clamps the age against it unconditionally,
     * so a particle that never touches anything costs the same arithmetic as one that does.
     */
    public static final float NEVER = 1e30f;

    /** Face encodings. Axis-aligned, because a block surface is; the low bit is the sign. */
    public static final int MINUS_X = 0;
    public static final int PLUS_X = 1;
    public static final int MINUS_Y = 2;
    public static final int PLUS_Y = 3;
    public static final int MINUS_Z = 4;
    public static final int PLUS_Z = 5;

    /**
     * How far a chord may cut the corner of the arc it stands in for, in blocks.
     *
     * <p>This is not about tunnelling: a probe walks the voxel grid along its whole segment, so a long
     * chord still finds the first block on it. It is only about the curve: what a chord misses is the
     * sagitta, and a tenth of a block of it is not a contact anyone can see in the wrong place.
     */
    private static final float CHORD_TOLERANCE = 0.1f;

    /**
     * A safety net on the number of chords one sweep may cut, not a budget it is expected to spend.
     *
     * <p>It used to be a budget: the step was sized once from the speed at birth and then stretched to fit
     * whatever was left, which quietly broke the one thing a chord has to be: short enough that crossing it
     * at a constant speed is a fair description of the arc. A particle that lives a minute got chords tens of
     * blocks long, the hit fraction along one mapped to a small fraction of the age it should have, and the
     * particle froze in mid-air above the floor it had been told it reached. Steps are sized from the
     * curvature at each point now, so this binds only on something pathological.
     */
    private static final int MAX_SEGMENTS = 64;

    private ParticleCollision() {
    }

    /** The contact of a particle that hits nothing, which is also what a non-colliding style gets. */
    public static Contact none(float life) {
        return new Contact(life, NEVER, PLUS_Y, NEVER);
    }

    /**
     * Walks {@code style}'s closed form from the spawn state and reports the first block it runs into.
     *
     * @param radius how far from the surface the particle's <em>centre</em> should come to rest. A billboard
     *               wants 0; a mesh wants roughly half its extent, or it lands half-buried.
     */
    public static Contact predict(Probe probe, ParticleStyle style, double x, double y, double z,
                                  float velocityX, float velocityY, float velocityZ, float life, float radius) {
        if (probe == null || !style.collides() || life <= 0.0f) {
            return none(life);
        }

        Hit hit = new Hit();
        Vector3f velocity = new Vector3f(velocityX, velocityY, velocityZ);

        float first = sweep(probe, style, x, y, z, velocity, life, radius, hit);
        if (first >= NEVER) {
            return none(life);
        }

        if (style.response == ContactResponse.DIE) {
            // Nothing for the shader to do: a particle that dies at the wall is a particle with a shorter
            // life, and life is already a field.
            return new Contact(first, NEVER, PLUS_Y, NEVER);
        }
        if (style.response == ContactResponse.STOP && hit.normal == PLUS_Y) {
            return new Contact(life, first, hit.normal, first);
        }

        // A STOP that did not land on a floor falls through to the rebound below, and the reason is that
        // stopping dead is only physical on something that can hold the particle up. Rubble that clips a
        // wall, ash that touches one, a casing that hits a ceiling: stopping there glues it to the face
        // and leaves it hanging in mid-air for the rest of its life, which is the one thing about this
        // whole mechanism a player will notice is wrong. A STOP style carries no restitution and no
        // friction, so the rebound it gets is a dead drop from the point of contact: it loses
        // everything to the wall and then falls, and the second sweep settles it on whatever is under
        // the wall, which is where it should have ended up.
        int normal = hit.normal;
        Vector3f impact = ParticleMotion.position(style, new Vector3f(), velocity, first, new Vector3f());
        Vector3f rebound = ParticleMotion.velocity(style, velocity, first, new Vector3f());
        reflect(rebound, normal, style.restitution, style.friction, rebound);

        // The rebound is another closed-form flight, so where it lands is found exactly the same way.
        float second = sweep(probe, style, x + impact.x, y + impact.y, z + impact.z, rebound,
                life - first, radius, hit);
        return new Contact(life, first, normal, second >= NEVER ? NEVER : first + second);
    }

    /**
     * Splits {@code velocity} about an axis-aligned face and puts it back together as the rebound: the part
     * into the surface comes back scaled by {@code restitution}, the part along it survives {@code friction}.
     *
     * <p>A velocity already leaving the surface is only slowed, never flipped: reflecting it would drive
     * the particle back into the block it just left.
     */
    public static Vector3f reflect(Vector3f velocity, int normal, float restitution, float friction,
                                   Vector3f target) {
        int axis = normal >> 1;
        float sign = (normal & 1) == 1 ? 1.0f : -1.0f;
        float along = axis == 0 ? velocity.x : axis == 1 ? velocity.y : velocity.z;
        float bounced = sign * along < 0.0f ? -restitution * along : along;

        target.set(velocity)
                .mul(friction);
        if (axis == 0) {
            target.x = bounced;
        } else if (axis == 1) {
            target.y = bounced;
        } else {
            target.z = bounced;
        }
        return target;
    }

    /**
     * Where the particle is at {@code age}, contact included. The Java mirror of
     * {@code gemrender_particlePosition}, and the thing a caller asks when it wants to put a sound or a decal
     * where a piece of debris came to rest.
     */
    public static Vector3f positionAt(ParticleStyle style, Vector3f spawnPosition, Vector3f spawnVelocity,
                                      Contact contact, float age, Vector3f target) {
        float flight = Math.min(age, contact.contactAge());
        ParticleMotion.position(style, spawnPosition, spawnVelocity, flight, target);
        if (age <= contact.contactAge()) {
            return target;
        }

        Vector3f rebound = ParticleMotion.velocity(style, spawnVelocity, contact.contactAge(), new Vector3f());
        reflect(rebound, contact.normal(), style.restitution, style.friction, rebound);

        float settled = Math.max(0.0f, Math.min(age, contact.restAge()) - contact.contactAge());
        return ParticleMotion.position(style, new Vector3f(target), rebound, settled, target);
    }

    /**
     * The velocity at {@code age}, contact included.
     *
     * <p>A particle that has come to rest keeps the velocity it arrived with rather than reporting zero,
     * because a mesh particle is oriented along this vector: a chunk of rubble that landed should lie the way
     * it hit, not snap upright the instant it stops.
     */
    public static Vector3f velocityAt(ParticleStyle style, Vector3f spawnVelocity, Contact contact, float age,
                                      Vector3f target) {
        float flight = Math.min(age, contact.contactAge());
        ParticleMotion.velocity(style, spawnVelocity, flight, target);
        if (age <= contact.contactAge() || contact.restAge() <= contact.contactAge()) {
            return target;
        }

        Vector3f rebound = reflect(new Vector3f(target), contact.normal(), style.restitution, style.friction,
                new Vector3f());
        float settled = Math.max(0.0f, Math.min(age, contact.restAge()) - contact.contactAge());
        return ParticleMotion.velocity(style, rebound, settled, target);
    }

    /** {@link #velocityAt} until {@code restAge}, zero after: {@code gemrender_particleMotion}. Streak input. */
    public static Vector3f motionAt(ParticleStyle style, Vector3f spawnVelocity, Contact contact, float age,
                                    Vector3f target) {
        return age >= contact.restAge() ? target.zero() : velocityAt(style, spawnVelocity, contact, age, target);
    }

    /** Extent probes: the six axis offsets of a mesh particle, {@code radius} out. */
    private static final float[][] EXTENT = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static float sweep(Probe probe, ParticleStyle style, double originX, double originY, double originZ,
                               Vector3f velocity, float duration, float radius, Hit hit) {
        if (duration <= 0.0f) {
            return NEVER;
        }

        Vector3f start = new Vector3f();
        Vector3f previous = new Vector3f();
        Vector3f at = new Vector3f();
        Vector3f scratch = new Vector3f();
        Hit candidate = new Hit();
        // Centre ray + six radius offsets: a mesh's side never enters a wall it skims. Offset inside a block at
        // spawn => dropped (else stopped where it stands).
        boolean[] live = new boolean[EXTENT.length];
        if (radius > 0.0f) {
            for (int k = 0; k < EXTENT.length; k++) {
                live[k] = !probe.probe(originX, originY, originZ, originX + EXTENT[k][0] * radius,
                        originY + EXTENT[k][1] * radius, originZ + EXTENT[k][2] * radius, candidate);
            }
        }

        float age = 0.0f;
        for (int i = 0; i < MAX_SEGMENTS && age < duration; i++) {
            float step = Math.min(stepFrom(style, velocity, age, scratch), duration - age);
            float next = age + step;
            ParticleMotion.position(style, start, velocity, next, at);

            boolean centre = probe.probe(originX + previous.x, originY + previous.y, originZ + previous.z,
                    originX + at.x, originY + at.y, originZ + at.z, hit);
            float fraction = centre ? hit.fraction : Float.MAX_VALUE;
            boolean extent = false;
            for (int k = 0; k < EXTENT.length; k++) {
                if (!live[k]) {
                    continue;
                }
                float ox = EXTENT[k][0] * radius, oy = EXTENT[k][1] * radius, oz = EXTENT[k][2] * radius;
                if (probe.probe(originX + previous.x + ox, originY + previous.y + oy, originZ + previous.z + oz,
                        originX + at.x + ox, originY + at.y + oy, originZ + at.z + oz, candidate)
                        && candidate.fraction < fraction) {
                    fraction = candidate.fraction;
                    hit.fraction = candidate.fraction;
                    hit.normal = candidate.normal;
                    extent = true;
                }
            }
            if (centre || extent) {
                float contact = age + fraction * step;
                float chord = previous.distance(at);
                if (!extent && radius > 0.0f && chord > 1e-6f) {
                    // Centre hit, no live offset caught it first: back off in time rather than along the
                    // chord, so the adjustment can reach into an earlier segment. chord / step = arrival speed.
                    contact -= radius * step / chord;
                }
                return Math.max(0.0f, contact);
            }

            previous.set(at);
            age = next;
        }

        return NEVER;
    }

    /**
     * How long the arc stays straight enough, from {@code age}, for one chord to stand in for it.
     *
     * <p>Driven by how hard the path is bending <em>at that moment</em> rather than by how far it goes or by
     * how fast it set off. What bends a particle is whatever acceleration the closed form is applying (the
     * pull, less what drag is already taking back), and the sagitta a chord misses over one step of that is
     * about {@code bend * step^2 / 8}.
     *
     * <p>Measuring it locally is what makes this hold for a flight of any length. A particle at terminal
     * velocity has no acceleration left, so its path really is a straight line crossed at a constant speed,
     * and one chord describes the rest of it exactly however long that is; a particle still accelerating gets
     * short chords for as long as it is. Sizing the step once from the speed at birth gets both halves wrong
     * in opposite directions.
     */
    private static float stepFrom(ParticleStyle style, Vector3f spawnVelocity, float age, Vector3f scratch) {
        ParticleMotion.velocity(style, spawnVelocity, age, scratch);

        float bendX = -style.drag * scratch.x;
        float bendY = -style.gravity - style.dragY * scratch.y;
        float bendZ = -style.drag * scratch.z;
        float bend = (float) Math.sqrt(bendX * bendX + bendY * bendY + bendZ * bendZ);

        if (!(bend > 1e-4f)) {
            return Float.MAX_VALUE; // straight from here on; the caller clamps this to what is left
        }
        return (float) Math.sqrt(8.0f * CHORD_TOLERANCE / bend);
    }

    /**
     * What the sweep found, filled in place so a spawn does not allocate one of these per segment.
     */
    public static final class Hit {

        /** How far along the probed segment the surface is, 0 to 1. */
        public float fraction;

        /** The face that was hit, as one of the six constants on {@link ParticleCollision}. */
        public int normal;
    }

    /**
     * The world, as much of it as a sweep needs: given a segment, is there a block surface on it.
     */
    @FunctionalInterface
    public interface Probe {

        /**
         * @param into filled with the contact when this returns {@code true}; untouched otherwise
         * @return whether the segment runs into anything
         */
        boolean probe(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, Hit into);
    }

    /**
     * A particle's whole future, in four numbers.
     *
     * @param life       how long it lives, which a {@link ContactResponse#DIE} style shortens to the contact
     * @param contactAge seconds from spawn to the first surface, or {@link #NEVER}
     * @param normal     the face of that surface
     * @param restAge    seconds from spawn to where it settles, or {@link #NEVER} if it never does. Equal to
     *                   {@code contactAge} when the particle stops dead on contact
     */
    public record Contact(float life, float contactAge, int normal, float restAge) {

        public boolean hits() {
            return contactAge < NEVER;
        }
    }
}
