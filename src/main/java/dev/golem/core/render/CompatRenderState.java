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
 * touches the world off-thread. Older versions have no such split: they hand the block entity itself
 * to a single {@code render} method on the render thread.
 *
 * <p>Golem keeps the 26.1 shape everywhere. On 26.1 this simply <b>is</b> a vanilla
 * {@link BlockEntityRenderState}, so renderers plug into the vanilla pipeline with no wrapping. On
 * older versions it is a plain holder whose fields the adapter fills in before calling
 * {@link CompatBER#submit}, which yields the same programming model with the extract step happening
 * immediately before the submit rather than in a separate pass.
 *
 * <p>Only the base fields consumers actually read are mirrored ({@code blockPos},
 * {@code lightCoords}); add more here as they are needed rather than mirroring all of vanilla's.
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
