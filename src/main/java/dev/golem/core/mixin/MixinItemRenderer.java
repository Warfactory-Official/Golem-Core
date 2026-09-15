package dev.golem.core.mixin;

//? if <26.1 {

/*import dev.golem.core.render.ItemRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/^*
 * Carries vanilla's held-item entity through the ownerless legacy item-render callback.
 *
 * <p>Vanilla has no callback at this boundary, and neither the NeoForge BEWLR nor Fabric builtin
 * item-renderer hook exposes the enclosing entity. An access widener can only change visibility; it
 * cannot carry this method-local argument. This is therefore the smallest remaining hook: two void
 * injections around vanilla's public owner-aware overload, with no rendering or fallback behavior.
 ^/
@Mixin(net.minecraft.client.renderer.entity.ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", at = @At("HEAD"))
    private void golem$pushItemOwner(LivingEntity owner, ItemStack stack, ItemDisplayContext context,
            boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, Level level, int light,
            int overlay, int seed, CallbackInfo ci) {
        ItemRenderContext.push(owner);
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", at = @At("TAIL"))
    private void golem$popItemOwner(LivingEntity owner, ItemStack stack, ItemDisplayContext context,
            boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, Level level, int light,
            int overlay, int seed, CallbackInfo ci) {
        ItemRenderContext.pop();
    }
}
*///?}
