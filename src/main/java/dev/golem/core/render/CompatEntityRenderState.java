package dev.golem.core.render;

/**
 * Base render state for an entity renderer, mirroring {@link CompatRenderState} on the entity side.
 *
 * <p>26.1 renders entities in two passes, extract then submit, and this object carries data between them, so
 * there it is a real {@code EntityRenderState}. Older versions have no such split; there this is a plain class
 * that {@link CompatEntityRenderer} allocates and fills itself.
 */
public class CompatEntityRenderState
        //? if >=26.1 {
        extends net.minecraft.client.renderer.entity.state.EntityRenderState
        //?}
{

    //? if <26.1 {
    /*/^*
     * Packed lightmap coordinates for this entity. 26.1's EntityRenderState carries this and vanilla
     * fills it during extraction; this era passes it straight to {@code render} instead, so
     * {@link CompatEntityRenderer} copies it here before extracting to keep the field available either
     * way.
     ^/
    public int lightCoords;
    *///?}
}
