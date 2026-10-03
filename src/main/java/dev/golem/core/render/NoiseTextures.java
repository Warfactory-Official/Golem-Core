package dev.golem.core.render;

//? if >=26.1 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
//?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;
*///?}

/**
 * The sampling state HBM 1.12 gave its noise textures ({@code noise_1}, {@code noise_2}): an ordinary texture-manager
 * texture (no mipmaps, {@code GL_NEAREST} minification, {@code GL_REPEAT}) whose magnification filter
 * {@code ResourceManager.init} then switched to {@code GL_LINEAR}. So a noise lookup that magnifies is bilinear and one
 * that minifies is nearest; an explicit level-0 lookup in a vertex shader always magnifies.
 */
public final class NoiseTextures {

    private NoiseTextures() {}

    //? if >=26.1 {
    /** Repeat, minification nearest, magnification linear, no mipmaps. */
    public static GpuSampler sampler() {
        return RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT, FilterMode.NEAREST,
                FilterMode.LINEAR, false);
    }
    //?} else {
    /*/^* Puts {@code texture} into that state (binds it on the active unit). ^/
    public static void apply(AbstractTexture texture) {
        texture.setFilter(false, false);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    }
    *///?}
}
