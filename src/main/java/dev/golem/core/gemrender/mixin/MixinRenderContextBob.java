package dev.golem.core.gemrender.mixin;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * GemRender 0.1.14 builds its 26.1 view from the camera state's projection, without the view bob 26.1 vanilla multiplies
 * into its own copy, so instanced models swayed against the world. Only applied to 0.1.14 ({@link GemRenderMixinPlugin}).
 *
 * <p>Why a mixin (AGENTS.md section 7): vanilla keeps the bobbed matrix in a local of {@code renderLevel}, GemRender's
 * API takes no projection, and nothing is hidden for an access widener to open.
 */
@Mixin(targets = "dev.engine_room.flywheel.impl.event.RenderContextImpl", remap = false)
public abstract class MixinRenderContextBob {

    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true)
    private static Matrix4f golem$withViewBob(Matrix4f projection) {
        GameRenderer renderer = Minecraft.getInstance().gameRenderer;
        GameRenderState state = renderer.getGameRenderState();
        CameraRenderState camera = state.levelRenderState.cameraRenderState;
        PoseStack bob = new PoseStack();
        GameRendererBob invoker = (GameRendererBob) renderer;
        invoker.golem$bobHurt(camera, bob);
        if (state.optionsRenderState.bobView) invoker.golem$bobView(camera, bob);
        return new Matrix4f(projection).mul(bob.last().pose());
    }
}
//?}
