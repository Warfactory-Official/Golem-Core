package dev.golem.core.render;

//? if >=1.21.11 {
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/** Textureless SRC_ALPHA/ONE lightning sheets. */
public final class LightningRenderType {
    private LightningRenderType() {}

    //? if >=1.21.11 {
    private static final RenderType TYPE = RenderType.create("golem_lightning",
            RenderSetup.builder(RenderPipelines.LIGHTNING).createRenderSetup());
    //?} else {
    /*private static final RenderType TYPE = RenderType.lightning();
    *///?}

    public static RenderType get() { return TYPE; }
}
