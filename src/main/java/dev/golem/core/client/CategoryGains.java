package dev.golem.core.client;

//? if <1.21.11 {
/*import dev.golem.core.mixin.SoundManagerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

/^*
 * The per-category gain 1.21.11 added to {@code SoundEngine}, for older nodes: {@code MixinLegacySoundGain} multiplies
 * the volume of each category by it and refreshes playing channels as {@code refreshCategoryVolume} does on 1.21.11.
 ^/
public final class CategoryGains {

    private static final float[] GAINS = new float[SoundSource.values().length];

    static {
        java.util.Arrays.fill(GAINS, 1.0F);
    }

    private CategoryGains() {}

    public static float gain(SoundSource source) {
        return GAINS[source.ordinal()];
    }

    static void set(SoundSource source, float gain) {
        GAINS[source.ordinal()] = Math.max(0.0F, Math.min(1.0F, gain));
        ((Refresh) ((SoundManagerAccess) Minecraft.getInstance().getSoundManager()).golem$soundEngine())
                .golem$refreshCategoryVolume(source);
    }

    /^* Implemented on {@code SoundEngine} by {@code MixinLegacySoundGain}. ^/
    public interface Refresh {
        void golem$refreshCategoryVolume(SoundSource source);
    }
}
*///?}
