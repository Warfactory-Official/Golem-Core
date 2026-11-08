package dev.golem.core.mixin;

// Fabric API has no FOV multiplier event (Forge-like loaders fire ComputeFovModifierEvent, used by Fov instead), no
// vanilla hook reads one, and an access widener cannot add a callback. The return-value modifier chains with others.
//? if fabric {

/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.golem.core.client.Fov;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public class MixinFovMultiplier {

    @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
    private float golem$fovMultiplier(float multiplier) {
        return (Object) this == Minecraft.getInstance().player ? Fov.applyMultiplier(multiplier) : multiplier;
    }
}
*///?}
