package dev.golem.core.mixin;

//? if <1.21.11 {
/*import dev.golem.core.client.CategoryGains;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/^*
 * {@code Sounds.setCategoryGain} below 1.21.11, which has no per-category gain. The vanilla {@code updateCategoryVolume}
 * cannot stand in: it re-reads the option and stops a channel at 0, where 1.21.11 keeps it playing silently. An
 * access widener cannot add the multiplication, so this is a mixin; {@code getVolume} is the one place every channel
 * volume is computed from.
 ^/
@Mixin(SoundEngine.class)
public abstract class MixinLegacySoundGain implements CategoryGains.Refresh {

    @Shadow @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow
    private float calculateVolume(SoundInstance sound) {
        throw new AssertionError("mixin shadow not applied");
    }

    @Inject(method = "getVolume(Lnet/minecraft/sounds/SoundSource;)F", at = @At("RETURN"), cancellable = true)
    private void golem$categoryGain(SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (source != null) cir.setReturnValue(cir.getReturnValueF() * CategoryGains.gain(source));
    }

    @Override
    public void golem$refreshCategoryVolume(SoundSource source) {
        instanceToChannel.forEach((sound, handle) -> {
            if (source == SoundSource.MASTER || sound.getSource() == source) {
                float volume = calculateVolume(sound);
                handle.execute(channel -> channel.setVolume(volume));
            }
        });
    }
}
*///?}
