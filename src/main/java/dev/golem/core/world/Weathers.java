package dev.golem.core.world;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * A level's own weather, the stand-in for 1.7's {@code WorldProvider.updateWeather} override: a mod that kept a
 * dimension's rain and thunder itself (per planet, per world type, ...) set {@code worldObj.rainingStrength} and
 * {@code thunderingStrength} there every tick instead of letting the overworld's weather drive it.
 *
 * <p><b>Why vanilla cannot.</b> Every sky-lit level without a ceiling runs vanilla's weather cycle
 * ({@code ServerLevel.advanceWeatherCycle}) from the server's single weather data (before 26.1 the overworld's level
 * data, which other dimensions see read-only), so rain in the overworld is rain in every such dimension. A
 * {@link Provider} answering for a level replaces that cycle for it: each level tick, in place of vanilla's, the level's
 * rain and thunder levels become the provider's and are sent to the level's players with vanilla's own packets
 * ({@code RAIN_LEVEL_CHANGE}, {@code THUNDER_LEVEL_CHANGE}, {@code START_RAINING} / {@code STOP_RAINING}), so everything
 * that reads the level's weather - the client's rain and sky, lightning, snow and ice, cauldrons, the sky darkening -
 * follows it. A level no provider answers for keeps vanilla's cycle.
 *
 * <p>The levels are set without vanilla's one-tick interpolation on the server ({@code setRainLevel}), which is how the
 * client receives them anyway. Hook: {@code MixinServerLevelWeather} (HEAD of {@code advanceWeatherCycle}).
 */
public final class Weathers {

    private Weathers() {}

    /** A level's weather this tick: rain strength and thunder strength (1.7 {@code rainingStrength} and
     *  {@code thunderingStrength}, before the thunder is weighted by the rain), both 0..1. */
    public record State(float rain, float thunder) {}

    public interface Provider {
        /** The weather {@code level} has this tick, or null to leave it to the next provider (vanilla at the end). */
        @Nullable State weather(ServerLevel level);
    }

    /** Every provider, in phase order; the first non-null answer wins. */
    public static final dev.golem.core.hook.Hook<Provider> PROVIDERS = dev.golem.core.hook.Hook.notifying();

    /** The last levels sent per level, so a change is sent once (vanilla compares its previous-tick fields). */
    private static final Map<ServerLevel, float[]> SENT = new WeakHashMap<>();

    public static void register(Provider provider) {
        PROVIDERS.register(provider);
    }

    /** The weather a provider gives {@code level} now, or null when vanilla's cycle runs there. */
    public static @Nullable State weather(ServerLevel level) {
        if (PROVIDERS.isEmpty()) return null;
        for (Provider provider : PROVIDERS) {
            State state = provider.weather(level);
            if (state != null) return state;
        }
        return null;
    }

    // ======================================================================== hook (MixinServerLevelWeather only)

    /**
     * Runs a provider's weather for {@code level} in place of vanilla's cycle; false when no provider answers (vanilla
     * then runs). Mirrors the tail of vanilla's {@code advanceWeatherCycle}: the level changes are sent to the level's
     * players when they change, and the start/stop of rain when {@code isRaining()} flips.
     */
    public static boolean advance(ServerLevel level) {
        State state = weather(level);
        if (state == null) return false;
        boolean wasRaining = level.isRaining();
        float rain = clamp(state.rain());
        float thunder = clamp(state.thunder());
        level.setRainLevel(rain);
        level.setThunderLevel(thunder);

        float[] sent;
        synchronized (SENT) {
            sent = SENT.computeIfAbsent(level, l -> new float[] {Float.NaN, Float.NaN});
        }
        var players = level.getServer().getPlayerList();
        if (sent[0] != rain) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain), level.dimension());
        }
        if (sent[1] != thunder) {
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder), level.dimension());
        }
        if (wasRaining != level.isRaining()) {
            players.broadcastAll(new ClientboundGameEventPacket(wasRaining ? ClientboundGameEventPacket.STOP_RAINING
                    : ClientboundGameEventPacket.START_RAINING, 0.0F), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain), level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder), level.dimension());
        }
        sent[0] = rain;
        sent[1] = thunder;
        return true;
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }
}
