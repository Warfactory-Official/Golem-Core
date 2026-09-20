package dev.golem.core.mixin;

import dev.golem.core.world.BoatControl;
import dev.golem.core.world.BoatInputs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands vanilla's private {@code controlBoat} to a boat that implements {@link BoatControl}. The
 * steering class moved from {@code Boat} to {@code AbstractBoat} at 1.21.2; the flags and the method
 * are the same on both.
 */
//? if >=26.1 {
@Mixin(net.minecraft.world.entity.vehicle.boat.AbstractBoat.class)
//?} else {
/*@Mixin(net.minecraft.world.entity.vehicle.boat.Boat.class)
*///?}
public abstract class MixinBoatControl implements BoatInputs {

    @Shadow
    private boolean inputLeft;

    @Shadow
    private boolean inputRight;

    @Shadow
    private boolean inputUp;

    @Shadow
    private boolean inputDown;

    @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
    private void golem$controlBoat(CallbackInfo ci) {
        if (this instanceof BoatControl control) {
            control.golem$controlBoat();
            ci.cancel();
        }
    }

    @Override
    public boolean golem$inputLeft() {
        return inputLeft;
    }

    @Override
    public boolean golem$inputRight() {
        return inputRight;
    }

    @Override
    public boolean golem$inputUp() {
        return inputUp;
    }

    @Override
    public boolean golem$inputDown() {
        return inputDown;
    }
}
