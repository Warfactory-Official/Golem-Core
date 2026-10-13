package dev.golem.core.mixin;

//? if >=26.1 {
import dev.golem.core.render.CloudRendering;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1 Fabric's hook for {@link dev.golem.core.client.Clouds}: a registered level's cloud layers in place of vanilla's
 * clouds. Listed for Fabric 26.1 only; NeoForge 26.1 uses its {@code CustomCloudsRenderer} instead.
 *
 * <p>Why a mixin (AGENTS.md section 7): (1) vanilla 26.1 draws clouds from the dimension's environment attributes only
 * (colour and height; one layer, its own drift), with no per-level renderer; (2) Fabric API 26.1 removed
 * {@code DimensionRenderingRegistry} and its {@code LevelRenderEvents} have no cloud stage, and drawing in another stage
 * would miss the clouds target under Fabulous, which only the clouds pass declares; (3) widening cannot add a
 * call. One cancellable HEAD inject on {@code CloudRenderer.render}; it cancels only for a level registered with
 * {@code Clouds}, and never for Golem's own per-layer renderers (they are {@code CloudRenderer}s too). Sodium 0.9
 * replaces only {@code buildMesh}, so its meshes serve both.
 */
@Mixin(CloudRenderer.class)
public abstract class MixinCloudRender {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void golem$clouds(int color, CloudStatus status, float bottomY, int range, Vec3 camera, long gameTime,
                              float partialTicks, CallbackInfo ci) {
        if (CloudRendering.isLayerRenderer(this)) return;
        if (CloudRendering.render(status, range, camera)) ci.cancel();
    }
}
//?} else {
/*public final class MixinCloudRender {
    // Empty below 26.1: Fabric uses DimensionRenderingRegistry there, Forge-likes MixinLegacyCloudRender.
    private MixinCloudRender() {}
}
*///?}
