package dev.golem.core.mixin;

//? if <26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import dev.golem.core.client.Clouds;
import dev.golem.core.render.CloudRendering;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Vanilla has no extra-cloud stage. Loader replacement callbacks precede the native pass.
 * Widening cannot append a draw, so HEAD retains replacement ownership and RETURN draws additions.
 ^/
@Mixin(LevelRenderer.class)
public abstract class MixinLegacyCloudRender {

    @Shadow
    private ClientLevel level;
    @Shadow private int ticks;
    //? if >=1.21 {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(PoseStack pose, Matrix4f frustum, Matrix4f projection, float partialTick, double camX,
                              double camY, double camZ, CallbackInfo ci) {
        if (!Clouds.nativeVisible(level)) { ci.cancel(); return; }
        //? if !fabric {
        if (!Clouds.replaces(level)) return;
        CloudRendering.renderLegacy(pose, frustum, projection, partialTick, camX, camY, camZ);
        ci.cancel();
        //?}
    }
    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void golem$additions(PoseStack pose, Matrix4f frustum, Matrix4f projection, float partialTick, double camX,
                                 double camY, double camZ, CallbackInfo ci) {
        if (!Clouds.additive(level) || !Clouds.nativeVisible(level)) return;
        var height = level.effects().getCloudHeight();
        if (Float.isNaN(height)) return;
        CloudRendering.renderAdditionsLegacy(pose, frustum, projection, partialTick, camX, camY, camZ,
                height, level.getCloudColor(partialTick), ticks);
    }
    //?} else {
    /^@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(PoseStack pose, Matrix4f projection, float partialTick, double camX, double camY,
                              double camZ, CallbackInfo ci) {
        if (!Clouds.nativeVisible(level)) { ci.cancel(); return; }
        //? if !fabric {
        if (!Clouds.replaces(level)) return;
        CloudRendering.renderLegacy(pose, null, projection, partialTick, camX, camY, camZ);
        ci.cancel();
        //?}
    }
    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void golem$additions(PoseStack pose, Matrix4f projection, float partialTick, double camX, double camY,
                                 double camZ, CallbackInfo ci) {
        if (!Clouds.additive(level) || !Clouds.nativeVisible(level)) return;
        var height = level.effects().getCloudHeight();
        if (Float.isNaN(height)) return;
        CloudRendering.renderAdditionsLegacy(pose, null, projection, partialTick, camX, camY, camZ,
                height, level.getCloudColor(partialTick), ticks);
    }
    ^///?}
}
*///?} else {
public final class MixinLegacyCloudRender {
    // Empty on 26.1: MixinCloudRender appends additions; NeoForge replacement uses CustomCloudsRenderer.
    private MixinLegacyCloudRender() {}
}
//?}
