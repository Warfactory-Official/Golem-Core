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
 * The 1.21.1 NeoForge / 1.20.1 Forge hook for {@link Clouds}: a registered level's cloud layers in place of vanilla's
 * clouds. Fabric uses Fabric API's {@code DimensionRenderingRegistry.registerCloudRenderer} at the same place instead.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla takes the clouds from the dimension type's effects object (one
 * layer at its fixed height, {@code getCloudColor}, 0.8 alpha, its own drift); (2) the Forge-likes' only seam is that
 * effects object's {@code renderClouds}, chosen by the dimension type's {@code effects} id. A mod's body dimensions
 * share {@code minecraft:overworld} with the overworld (the legacy dimension conversion), so overriding it would take
 * over the overworld's effects and collide with other mods, and a separate effects id per dimension changes how
 * shader packs (Iris) classify the dimension, which the 26.1 nodes do not; (3) widening cannot add a call.
 * One cancellable HEAD inject on {@code renderClouds}, before the effects check and before Sodium's own cloud inject
 * (at {@code getCloudHeight}); it cancels only for a level registered with {@code Clouds}.
 ^/
@Mixin(LevelRenderer.class)
public abstract class MixinLegacyCloudRender {

    @Shadow
    private ClientLevel level;

    //? if >=1.21 {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(PoseStack pose, Matrix4f frustum, Matrix4f projection, float partialTick, double camX,
                              double camY, double camZ, CallbackInfo ci) {
        if (!Clouds.has(level)) return;
        CloudRendering.renderLegacy(pose, frustum, projection, partialTick, camX, camY, camZ);
        ci.cancel();
    }
    //?} else {
    /^@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(PoseStack pose, Matrix4f projection, float partialTick, double camX, double camY,
                              double camZ, CallbackInfo ci) {
        if (!Clouds.has(level)) return;
        CloudRendering.renderLegacy(pose, null, projection, partialTick, camX, camY, camZ);
        ci.cancel();
    }
    ^///?}
}
*///?} else {
public final class MixinLegacyCloudRender {
    // Empty on 26.1: NeoForge uses CustomCloudsRenderer, Fabric MixinCloudRender.
    private MixinLegacyCloudRender() {}
}
//?}
