package dev.golem.core.mixin;

// SwingReceiver on Fabric. Ladder (AGENTS.md section 7): vanilla has no swing callback (1); NeoForge and Forge
// answer it through their own item hook, IItemExtension/IForgeItem.onEntitySwing, which Core overrides on
// its item bases (2) - Fabric API has no swing event or item hook at all; an access widener (3) cannot add a
// call. The smallest hook is this HEAD inject into LivingEntity.swing(hand, sendToSwingingEntity), where the
// Forge-like loaders call their item hook; it cancels the swing when the item asks, as they do.
//? if fabric {

/*import dev.golem.core.world.SwingReceiver;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntitySwing {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void golem$itemSwing(InteractionHand hand, boolean sendToSwingingEntity, CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (SwingReceiver.dispatch(self.getItemInHand(hand), self, hand)) callback.cancel();
    }
}
*///?}
