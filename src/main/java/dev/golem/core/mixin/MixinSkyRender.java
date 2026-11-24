package dev.golem.core.mixin;

//? if >=1.21.11 {
import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.render.Skyboxes;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.golem.core.client.FrameTime;
import dev.golem.core.render.Passes;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}

/**
 * The two {@link Skyboxes} seams inside vanilla's overworld sky: replace the sun, moon and stars, and draw after the
 * whole sky.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla has no extension point inside {@code SkyRenderer}; (2) Fabric 26.1
 * has no sky callback, and NeoForge 26.1's {@code CustomSkyboxRenderer} replaces the entire sky, so it cannot keep
 * vanilla's disc and dark disc around a replaced sun and moon. {@code RenderLevelStageEvent.AfterSky} could serve the
 * after-sky draw alone, but one hook keeps both loaders' draw order identical; (3) widening cannot add a call.
 * Three injects: HEAD of {@code renderSunMoonAndStars} (cancels only when a registered skybox replaces the celestial
 * bodies this frame), its RETURN, and RETURN of {@code renderDarkDisc}. 26.3 has one inject at HEAD of {@code render}
 * instead. With no skybox registered they do nothing.
 */
@Mixin(SkyRenderer.class)
public abstract class MixinSkyRender {

    //? if >=26.3 {
    /*@Shadow
    @Final
    private RenderTarget renderTarget;

    @Shadow
    protected abstract void renderSkyDisc(RenderPass renderPass, Vector3fc skyColor);

    @Shadow
    protected abstract void renderSunriseAndSunset(RenderPass renderPass, PoseStack poseStack, float sunAngle,
                                                   Vector4fc sunriseAndSunsetColor);

    @Shadow
    protected abstract void renderSunMoonAndStars(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle,
                                                  float starAngle, MoonPhase moonPhase, float rainBrightness,
                                                  float starBrightness);

    @Shadow
    protected abstract void renderDarkDisc(RenderPass renderPass);

    /^* 26.3 draws the sky in one pass that Core cannot draw inside; with a skybox registered its parts get passes of their own. ^/
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golem$sky(GpuBufferSlice skyFog, SkyRenderState state, CallbackInfo ci) {
        if (state.skybox == DimensionType.Skybox.END || Skyboxes.SKYBOXES.isEmpty()) return;
        ci.cancel();
        RenderSystem.setShaderFog(skyFog);
        GpuTextureView color = renderTarget.getColorTextureView();
        GpuTextureView depth = renderTarget.getDepthTextureView();
        PoseStack pose = new PoseStack();
        try (RenderPass pass = Passes.open(() -> "Sky", color, depth)) {
            RenderSystem.bindDefaultUniforms(pass);
            renderSkyDisc(pass, state.skyColor);
            renderSunriseAndSunset(pass, pose, state.sunAngle, state.sunriseAndSunsetColor);
        }
        if (!Skyboxes.celestial(pose, FrameTime.partialTick(), state.sunAngle / (float) (Math.PI * 2.0),
                state.moonPhase.index())) {
            try (RenderPass pass = Passes.open(() -> "Sky", color, depth)) {
                RenderSystem.bindDefaultUniforms(pass);
                renderSunMoonAndStars(pass, pose, state.sunAngle, state.moonAngle, state.starAngle, state.moonPhase,
                        state.rainBrightness, state.starBrightness);
            }
        }
        if (state.shouldRenderDarkDisc) {
            try (RenderPass pass = Passes.open(() -> "Sky", color, depth)) {
                RenderSystem.bindDefaultUniforms(pass);
                renderDarkDisc(pass);
            }
        }
        Skyboxes.afterSky();
    }
    *///?} else {
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
    //?}
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
