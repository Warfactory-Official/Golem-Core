package dev.golem.core.render;

//? if >=26.1 {
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
//?} else {
/*import net.minecraft.core.BlockPos;
*///?}

/**
 * Base class for block-entity render states.
 *
 * <p>26.1 splits block-entity rendering into an <i>extract</i> pass (read the block entity into a
 * plain data object) and a <i>submit</i> pass (turn that data into draw calls), so the renderer never
 * touches the world off-thread. Older versions hand the block entity itself to a single {@code render}
 * method on the render thread.
 *
 * <p>The 26.1 shape is kept everywhere. On 26.1 this <b>is</b> a vanilla {@link BlockEntityRenderState}.
 * On older versions it is a plain holder whose fields the adapter fills in immediately before calling
 * {@link CompatBER#submit}.
 *
 * <p>Only the base fields consumers read are mirrored ({@code blockPos}, {@code lightCoords}).
 */
public class CompatRenderState
        //? if >=26.1 {
        extends BlockEntityRenderState
        //?}
{
    //? if <26.1 {
    /*public BlockPos blockPos = BlockPos.ZERO;
    public int lightCoords;
    *///?}
}
