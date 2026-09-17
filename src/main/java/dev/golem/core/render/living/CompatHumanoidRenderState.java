package dev.golem.core.render.living;

/**
 * Render state of a {@link CompatHumanoidMobRenderer}.
 *
 * <p>On 26.1 this is a {@code ZombieRenderState} rather than a plain {@code HumanoidRenderState} so
 * that the same renderer can wear vanilla's zombie body, whose model is typed against the zombie
 * state; the two extra fields it adds cost nothing for a body that ignores them. Below 26.1 there are
 * no render states and the renderer reads the entity directly, so only the variant survives here.
 */
public class CompatHumanoidRenderState
        //? if >=26.1 {
        extends net.minecraft.client.renderer.entity.state.ZombieRenderState
        //?}
{
    /** Which of the renderer's bodies and skins this frame wears. */
    public int variant;
}
