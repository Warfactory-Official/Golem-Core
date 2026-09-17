package dev.golem.core.render.living;

/**
 * Render state for a living entity drawn by a {@link CompatMobRenderer}, mirroring
 * {@link dev.golem.core.render.CompatEntityRenderState} one level down the vanilla hierarchy.
 *
 * <p>On 26.1 this must be a real {@code LivingEntityRenderState}, because vanilla's living renderer and
 * every vanilla mob model are typed against it. Older versions have no render states; there this is a
 * plain class the facade allocates and fills from the entity before vanilla's single-pass render runs,
 * carrying the same fields under the same names so a model or renderer written against 26.1's shape
 * reads them unchanged.
 *
 * <p>Below 26.1 the walk/head fields are filled twice: from the entity before the pass (so
 * {@code scale} and {@code texture} hooks can read them), and again from vanilla's own values when the
 * model's {@code setupAnim} runs, which is what vanilla itself animates from.
 */
public class CompatLivingRenderState
        //? if >=26.1 {
        extends net.minecraft.client.renderer.entity.state.LivingEntityRenderState
        //?}
{

    //? if <26.1 {
    /*public int lightCoords;
    public float ageInTicks;
    public float bodyRot;
    /^* Head yaw relative to the body, degrees. ^/
    public float yRot;
    /^* Head pitch, degrees. ^/
    public float xRot;
    public float deathTime;
    public float walkAnimationPos;
    public float walkAnimationSpeed;
    public float scale = 1F;
    public boolean isBaby;
    public boolean isInvisible;
    public boolean hasRedOverlay;
    public boolean isFullyFrozen;
    /^* Vanilla's outline colour field; always "none" below 26.1, kept so callers need no guard. ^/
    public int outlineColor;
    *///?}

    /** The attack swing, 0..1; a 26.1 {@code ArmedEntityRenderState} field, so it lives here for the rest. */
    public float attackTime;
}
