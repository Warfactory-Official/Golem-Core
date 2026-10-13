package dev.golem.core.mixin;

//? if >=26.1 {
import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.Skyboxes;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two {@link Skyboxes} seams inside vanilla's overworld sky: replace the sun, moon and stars, and draw after the
 * whole sky.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no extension point inside {@code SkyRenderer}; (2) Fabric 26.1
 * has no sky callback, and NeoForge 26.1's {@code CustomSkyboxRenderer} replaces the entire sky, so it cannot keep
 * vanilla's disc and dark disc around a replaced sun and moon. {@code RenderLevelStageEvent.AfterSky} could serve the
 * after-sky draw alone, but one hook keeps both loaders' draw order identical; (3) widening cannot add a call.
 * Three injects: HEAD of {@code renderSunMoonAndStars} (cancels only when a registered skybox replaces the celestial
 * bodies this frame), its RETURN, and RETURN of {@code renderDarkDisc}. With no skybox registered they do nothing.
 */
@Mixin(SkyRenderer.class)
public abstract class MixinSkyRender {

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void golem$celestial(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
                                 float rainBrightness, float starBrightness, CallbackInfo ci) {
        if (Skyboxes.celestial(poseStack, dev.golem.core.client.FrameTime.partialTick(),
                sunAngle / (float) (Math.PI * 2.0), moonPhase.index())) {
            if (!Skyboxes.darkDiscFollows()) Skyboxes.afterSky();
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"))
    private void golem$afterCelestial(PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
                                      float rainBrightness, float starBrightness, CallbackInfo ci) {
        if (!Skyboxes.darkDiscFollows()) Skyboxes.afterSky();
    }

    @Inject(method = "renderDarkDisc", at = @At("RETURN"))
    private void golem$afterDarkDisc(CallbackInfo ci) {
        Skyboxes.afterSky();
    }
}
//?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.golem.core.render.Skyboxes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * The two {@link Skyboxes} seams inside vanilla's overworld sky before 26.1: replace the sun, moon and stars, and draw
 * after the whole sky.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla draws the whole sky in one {@code renderSky} method with no
 * extension point between its parts; (2) Forge/NeoForge's {@code DimensionSpecialEffects.renderSky} and Fabric's
 * {@code DimensionRenderingRegistry} sky renderer replace the entire sky. Fabric's cannot fall back to vanilla on
 * frames where nothing is replaced, and overriding the overworld's effects object on Forge-likes also takes over its
 * fog, cloud and sunrise behaviour. Fabric has no after-sky event, and one hook keeps both loaders' draw order
 * identical; (3) widening cannot add a call. Two injects: at the first {@code blendFuncSeparate} (cancels only when a
 * registered skybox replaces the celestial bodies this frame, then repeats vanilla's tail, the colour reset and dark
 * disc, itself) and at the final {@code depthMask(true)}, which only the overworld-type branch reaches. With no
 * skybox registered they do nothing.
 ^/
@Mixin(LevelRenderer.class)
public abstract class MixinSkyRender {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ClientLevel level;

    @Shadow
    private VertexBuffer darkBuffer;

    //? if >=1.21 {
    @Inject(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V"),
            cancellable = true)
    private void golem$celestial(Matrix4f frustum, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
                                 Runnable skyFog, CallbackInfo ci) {
        PoseStack pose = new PoseStack();
        pose.mulPose(frustum);
        if (golem$replaceCelestial(pose, projection, partialTick)) ci.cancel();
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1, target = "Lcom/mojang/blaze3d/systems/RenderSystem;depthMask(Z)V"))
    private void golem$afterSky(Matrix4f frustum, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
                                Runnable skyFog, CallbackInfo ci) {
        PoseStack pose = new PoseStack();
        pose.mulPose(frustum);
        Skyboxes.afterSky(pose, partialTick, level.getTimeOfDay(partialTick), level.getMoonPhase());
    }
    //?} else {
    /^@Inject(method = "renderSky", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lcom/mojang/blaze3d/systems/RenderSystem;blendFuncSeparate(Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;Lcom/mojang/blaze3d/platform/GlStateManager$SourceFactor;Lcom/mojang/blaze3d/platform/GlStateManager$DestFactor;)V"),
            cancellable = true)
    private void golem$celestial(PoseStack pose, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
                                 Runnable skyFog, CallbackInfo ci) {
        if (golem$replaceCelestial(pose, projection, partialTick)) ci.cancel();
    }

    @Inject(method = "renderSky", at = @At(value = "INVOKE", ordinal = 1, target = "Lcom/mojang/blaze3d/systems/RenderSystem;depthMask(Z)V"))
    private void golem$afterSky(PoseStack pose, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
                                Runnable skyFog, CallbackInfo ci) {
        Skyboxes.afterSky(pose, partialTick, level.getTimeOfDay(partialTick), level.getMoonPhase());
    }
    ^///?}

    /^* Draws a replacement and repeats vanilla's tail after its sun, moon and stars; false leaves vanilla to it. ^/
    private boolean golem$replaceCelestial(PoseStack pose, Matrix4f projection, float partialTick) {
        float angle = level.getTimeOfDay(partialTick);
        int phase = level.getMoonPhase();
        if (!Skyboxes.celestial(pose, partialTick, angle, phase)) return false;
        // Vanilla's own tail from here: colour and blend reset, then the dark disc below the horizon.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
        double horizon = minecraft.player.getEyePosition(partialTick).y - level.getLevelData().getHorizonHeight(level);
        if (horizon < 0.0) {
            pose.pushPose();
            pose.translate(0.0F, 12.0F, 0.0F);
            darkBuffer.bind();
            darkBuffer.drawWithShader(pose.last().pose(), projection, GameRenderer.getPositionShader());
            VertexBuffer.unbind();
            pose.popPose();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Skyboxes.afterSky(pose, partialTick, angle, phase);
        RenderSystem.depthMask(true);
        return true;
    }
}
*///?}
