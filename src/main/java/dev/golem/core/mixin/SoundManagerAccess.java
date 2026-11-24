package dev.golem.core.mixin;

//? if <1.21.11 {
/*import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/^* The engine behind the manager, for {@code CategoryGains} below 1.21.11 (1.21.11 forwards the gain itself). ^/
@Mixin(SoundManager.class)
public interface SoundManagerAccess {

    @Accessor("soundEngine")
    SoundEngine golem$soundEngine();
}
*///?}
