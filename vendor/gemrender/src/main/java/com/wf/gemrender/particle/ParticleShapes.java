package com.wf.gemrender.particle;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Java mirror of the attitude functions in {@code particle.glsl} for {@link GemRenderParticleTypes#DECAL},
 * {@link GemRenderParticleTypes#STREAK} and {@link GemRenderParticleTypes#BODY}. {@code ParticleShapesGlTest}
 * holds the two together.
 */
public final class ParticleShapes {
    public static final float DECAL_LIFT = 1.0f / 512.0f;

    private static final float PI = 3.14159265f;

    private ParticleShapes() {
    }

    /** Columns {@code (t, b, n)}, right-handed, rolled about {@code n}. */
    public static Matrix3f planeBasis(Vector3fc normal, float roll, Matrix3f target) {
        Vector3f n = new Vector3f(normal);
        Vector3f reference = Math.abs(n.y) > 0.999f ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f t = reference.cross(n, new Vector3f()).normalize();
        Vector3f b = n.cross(t, new Vector3f());
        float c = (float) Math.cos(roll);
        float s = (float) Math.sin(roll);
        Vector3f tr = new Vector3f(t).mul(c).fma(s, b);
        Vector3f br = new Vector3f(b).mul(c).fma(-s, t);
        return target.setColumn(0, tr).setColumn(1, br).setColumn(2, n);
    }

    /** Where a decal's quad corner {@code (cornerX, cornerY)} in {@code [-0.5, 0.5]} lands. */
    public static Vector3f decalCorner(Vector3fc position, Vector3fc normal, float roll, float cornerX, float cornerY,
                                       float size, Vector3f target) {
        Vector3f n = normal.lengthSquared() > 1e-12f ? new Vector3f(normal).normalize() : new Vector3f(0, 1, 0);
        Matrix3f basis = planeBasis(n, roll, new Matrix3f());
        return basis.transform(target.set(cornerX * size, cornerY * size, DECAL_LIFT)).add(position);
    }

    /**
     * Where a streak's quad corner lands; the head (cornerY 0.5) sits on {@code center}. {@code velocity} from
     * {@link ParticleCollision#motionAt}: settled => width x width dot.
     */
    public static Vector3f streakCorner(Vector3fc center, Vector3fc velocity, Vector3fc eye, Vector3fc fallbackRight,
                                        float cornerX, float cornerY, float width, float streak, Vector3f target) {
        float speed = velocity.length();
        Vector3f dir = speed > 1e-6f ? new Vector3f(velocity).div(speed) : new Vector3f(0.0f, 1.0f, 0.0f);
        float length = Math.max(streak * speed, width);
        Vector3f across = dir.cross(new Vector3f(eye).sub(center), new Vector3f());
        float acrossLength = across.length();
        Vector3f side = acrossLength > 1e-6f ? across.div(acrossLength) : new Vector3f(fallbackRight);
        return target.set(center)
                .fma(cornerX * width, side)
                .fma((cornerY - 0.5f) * length, dir);
    }

    public static float streakLength(Vector3fc velocity, float width, float streak) {
        return Math.max(streak * velocity.length(), width);
    }

    /** Attitude of a BODY particle at {@code age}: columns are the model's X, Y, Z in world space. */
    public static Matrix3f bodyBasis(ParticleStyle style, Vector3fc spawnVelocity, float spinPhase,
                                     ParticleCollision.Contact contact, float age, Matrix3f target) {
        float groundLen = (float) Math.sqrt(spawnVelocity.x() * spawnVelocity.x()
                + spawnVelocity.z() * spawnVelocity.z());
        float hx = groundLen > 1e-6f ? spawnVelocity.x() / groundLen : 0.0f;
        float hz = groundLen > 1e-6f ? spawnVelocity.z() / groundLen : 1.0f;
        float cy = (float) Math.cos(spinPhase);
        float sy = (float) Math.sin(spinPhase);
        Vector3f forward = new Vector3f(hx * cy - hz * sy, 0.0f, hx * sy + hz * cy);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f axis = up.cross(forward, new Vector3f());

        float angle = bodyAngle(style, spinPhase, contact, age);
        Vector3f y = new Vector3f(up).mul((float) Math.cos(angle)).fma((float) Math.sin(angle), forward);
        return target.setColumn(0, axis).setColumn(1, y).setColumn(2, axis.cross(y, new Vector3f()));
    }

    static float bodyAngle(ParticleStyle style, float spinPhase, ParticleCollision.Contact contact, float age) {
        float free = 2.0f * spinPhase + style.spinRate * Math.min(age, contact.restAge());
        if (age <= contact.contactAge()) {
            return free;
        }
        float rest = PI * ((float) Math.floor((2.0f * spinPhase + style.spinRate * contact.restAge()) / PI) + 0.5f);
        float span = contact.restAge() - contact.contactAge();
        float w = span > 1e-6f ? Math.max(0.0f, Math.min(1.0f, (age - contact.contactAge()) / span)) : 1.0f;
        return free + (rest - free) * w * w;
    }

    /** {@code gemrender_nearFade}. */
    public static float nearFade(Vector3fc center, Vector3fc eye, float size) {
        float d = center.distance(eye);
        return Math.max(0.0f, Math.min(1.0f, (d - NEAR_IN * size) / Math.max((NEAR_OUT - NEAR_IN) * size, 1e-6f)));
    }

    /** {@code gemrender_cullFade}. */
    public static float cullFade(Vector3fc center, Vector3fc eye, float cull) {
        return cull > 0.0f ? Math.max(0.0f, Math.min(1.0f, (center.distance(eye) - cull) / ParticleBuffer.CULL_RAMP))
                : 1.0f;
    }

    public static final float NEAR_IN = 0.5f;
    public static final float NEAR_OUT = 1.5f;

    public static float bodyScale(ParticleStyle style, float unitAge) {
        return 1.0f - ParticleMotion.fade(style, unitAge);
    }
}
