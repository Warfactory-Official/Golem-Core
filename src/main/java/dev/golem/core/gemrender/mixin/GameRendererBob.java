package dev.golem.core.gemrender.mixin;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's hurt tilt and view bob, for {@link MixinRenderContextBob}. Both are private and no API repeats them; an
 * access widener would open them on every 26.1 node for one GemRender release, while this invoker is applied only with
 * that patch.
 */
@Mixin(GameRenderer.class)
public interface GameRendererBob {

    @Invoker("bobHurt")
    void golem$bobHurt(CameraRenderState camera, PoseStack pose);

    @Invoker("bobView")
    void golem$bobView(CameraRenderState camera, PoseStack pose);
}
//?}
