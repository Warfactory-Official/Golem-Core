package dev.golem.core.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.Ids;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import dev.golem.core.platform.Lifecycle;
import dev.golem.core.platform.Platform;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * A level's time of day, the 1.7 {@code World.getWorldTime()}: the tick count the sun's angle is derived from, which
 * {@code /time set} and sleeping change and the daylight-cycle game rule stops. Distinct from the total game time
 * ({@code getGameTime()}), which only ever counts up.
 *
 * <p><b>Vanilla's clock.</b> Before 26.1 the day time is {@code Level.getDayTime()}, shared by every dimension with the
 * overworld. 26.1 moved it into world clocks: a dimension type names a {@code default_clock} and the day time is that
 * clock's total ticks ({@code Level.getDefaultClockTime()}); a dimension type without one reads 0. {@link #get} reads
 * whichever the node has.
 *
 * <p><b>A level's own clock</b> ({@link #ownClock}): 1.7 mods gave a dimension its own day by overriding
 * {@code WorldProvider.getWorldTime/setWorldTime} with a counter kept per world (hbm-space's
 * {@code CelestialBodyWorldSavedData}), advanced by one each world tick while {@code doDaylightCycle} is on and sent to
 * the client, which advanced its copy itself between updates. Golem Core keeps the same counter for each registered
 * dimension, on every node alike: saved per level ({@code golemcore:time_of_day}), advanced at the end of each server
 * tick while the daylight game rule ({@code advance_time} on 26.1) is on, and sent to the level's players when they
 * arrive, once a second, and whenever it is {@link #set}. A client derives the time between updates from its own game
 * time. 26.1's world clocks are not used: the older nodes have no registry for a {@code world_clock} entry, and
 * timelines, the bed rule and environment attributes would follow that clock too, so the nodes would disagree.
 * {@link #nativeClock} uses a dimension type's own 26.1 clock instead.
 */
public final class TimeOfDay {

    private TimeOfDay() {}

    private static final String DATA_ID = "golemcore:time_of_day";
    private static final Set<ResourceKey<Level>> OWN = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceKey<Level>> NATIVE = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceKey<Level>, Long> FIXED = new ConcurrentHashMap<>();
    /** Client: each own-clock dimension's last update, as (day time, client game time at receipt, advancing). */
    private static final Map<ResourceKey<Level>, long[]> CLIENT = new ConcurrentHashMap<>();
    private static boolean hooked;

    /**
     * Gives {@code dimension} a time of day of its own (see the class comment). Call at mod construction on both sides;
     * calling it again for the same dimension does nothing.
     */
    public static synchronized void ownClock(ResourceKey<Level> dimension) {
        if (NATIVE.contains(dimension)) throw new IllegalStateException(dimension + " already uses its native world clock");
        OWN.add(dimension);
        hook();
    }

    private static void hook() {
        if (hooked) return;
        hooked = true;
        GolemNetwork.declarePlayToClient(SyncPayload.CODEC);
        if (Platform.isClient()) {
            GolemNetwork.registerPlayToClient(SyncPayload.CODEC, (payload, context) -> {
                if (context == null || context.level() == null) return;
                CLIENT.put(payload.dimension(), new long[] {payload.time(), context.level().getGameTime(),
                        payload.advancing() ? 1 : 0});
            });
        }
        Lifecycle.onServerTick(TimeOfDay::tick);
        Lifecycle.onServerStarted(server -> {
            for (var entry : FIXED.entrySet()) {
                var level = server.getLevel(entry.getKey());
                if (level == null) continue;
                set(level, entry.getValue());
                //? if >=26.1 {
                server.clockManager().setPaused(level.dimensionType().defaultClock().orElseThrow(), true);
                //?}
            }
        });
        Lifecycle.onEntityLoad((entity, level) -> {
            if (entity instanceof ServerPlayer player && OWN.contains(level.dimension())) send(level, player);
        });
    }

    /** Uses the dimension type's own native world clock; Minecraft saves, ticks and synchronizes it. */
    public static synchronized void nativeClock(ResourceKey<Level> dimension) {
        //? if >=26.1 {
        if (OWN.contains(dimension)) throw new IllegalStateException(dimension + " already uses a saved time-of-day counter");
        NATIVE.add(dimension);
        hook();
        //?} else {
        /*throw new UnsupportedOperationException("Native world clocks require Minecraft 26.1");
        *///?}
    }

    /** Whether {@code level} keeps its own time of day ({@link #ownClock} or {@link #nativeClock}). */
    public static boolean hasOwnClock(Level level) {
        return OWN.contains(level.dimension()) || NATIVE.contains(level.dimension());
    }

    /** {@link #nativeClock} on 26.1, else {@link #ownClock}; call at mod construction on both sides. */
    public static synchronized void autoClock(ResourceKey<Level> dimension) {
        //? if >=26.1 {
        nativeClock(dimension);
        //?} else {
        /*ownClock(dimension);
        *///?}
    }

    /** Gives the dimension a constant own-clock time, including its first client frame. */
    public static synchronized void fixedClock(ResourceKey<Level> dimension, long time) {
        FIXED.put(dimension, time);
        autoClock(dimension);
    }
    /** Whether {@code level} runs on its dimension type's native world clock (false on every node below 26.1). */
    public static boolean isNative(Level level) {
        //? if >=26.1 {
        return NATIVE.contains(level.dimension());
        //?} else {
        /*return false;
        *///?}
    }

    /** Whether the dimension {@code dimension} keeps its own time of day ({@link #ownClock}). */
    public static boolean hasOwnClock(ResourceKey<Level> dimension) {
        return OWN.contains(dimension) || NATIVE.contains(dimension);
    }

    /**
     * Stops {@code dimension}'s own clock: it reads vanilla's day time again; the saved time stays in the level for a
     * later {@link #ownClock}. For tests, which have only the overworld to try a clock on.
     */
    public static void release(ResourceKey<Level> dimension) {
        OWN.remove(dimension);
        NATIVE.remove(dimension);
        FIXED.remove(dimension);
        CLIENT.remove(dimension);
    }

    /** The level's time of day in ticks: its own clock when it has one, otherwise vanilla's day time. */
    public static long get(Level level) {
        var fixed = FIXED.get(level.dimension());
        if (fixed != null) return fixed;
        //? if >=26.1 {
        if (NATIVE.contains(level.dimension())) {
            return level.clockManager().getTotalTicks(level.dimensionType().defaultClock().orElseThrow());
        }
        //?}
        if (OWN.contains(level.dimension())) {
            if (level instanceof ServerLevel server) return data(server).time;
            long[] state = CLIENT.get(level.dimension());
            if (state == null) return 0L;
            return state[0] + (state[2] != 0 ? Math.max(0L, level.getGameTime() - state[1]) : 0L);
        }
        return vanilla(level);
    }

    /**
     * Sets the level's time of day: its own clock when it has one (sent to its players at once), otherwise vanilla's
     * day time (before 26.1 shared by all dimensions with the overworld; on 26.1 the dimension type's default clock).
     */
    public static void set(ServerLevel level, long time) {
        long value = FIXED.getOrDefault(level.dimension(), time);
        //? if >=26.1 {
        if (NATIVE.contains(level.dimension())) {
            level.getServer().clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), value);
            return;
        }
        //?}
        if (OWN.contains(level.dimension())) {
            Data data = data(level);
            data.time = value;
            data.setDirty();
            for (ServerPlayer player : level.players()) send(level, player);
            return;
        }
        //? if >=26.1 {
        level.dimensionType().defaultClock()
             .ifPresent(clock -> level.getServer().clockManager().setTotalTicks(clock, value));
        //?} else {
        /*level.setDayTime(value);
        *///?}
    }

    /** Vanilla's day time for {@code level}, ignoring any own clock. */
    public static long vanilla(Level level) {
        //? if >=26.1 {
        return level.getDefaultClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }

    /** Whether the daylight cycle game rule lets time of day advance in {@code level}. */
    public static boolean advancing(ServerLevel level) {
        if (FIXED.containsKey(level.dimension())) return false;
        //? if >=26.1 {
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME);
        //?} else {
        /*return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        *///?}
    }

    private static void tick(MinecraftServer server) {
        for (ResourceKey<Level> key : OWN) {
            ServerLevel level = server.getLevel(key);
            if (level == null) continue;
            boolean advancing = advancing(level);
            Data data = data(level);
            if (advancing) {
                data.time++;
                data.setDirty();
            }
            if (level.getGameTime() % 20 == 0) {
                for (ServerPlayer player : level.players()) send(level, player);
            }
        }
    }

    private static void send(ServerLevel level, ServerPlayer player) {
        if (!GolemNetwork.canSendToPlayer(player, SyncPayload.CODEC)) return;
        GolemNetwork.sendToPlayer(player, SyncPayload.CODEC,
                new SyncPayload(level.dimension(), data(level).time, advancing(level)));
    }

    private static Data data(ServerLevel level) {
        return CompatSavedData.get(level, DATA_ID, Data::new, Data.CODEC);
    }

    /** One level's own clock; saved as {@code {time: <ticks>}} (1.7 {@code CelestialBodyData.time}). */
    static final class Data extends CompatSavedData<Data> {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(data -> data.time)
        ).apply(instance, Data::new));

        long time;

        Data() {
            this(0L);
        }

        Data(long time) {
            this.time = time;
        }

        @Override
        protected Codec<Data> codec() {
            return CODEC;
        }
    }

    /** S2C: one own-clock dimension's time of day now, and whether it is advancing. */
    record SyncPayload(ResourceKey<Level> dimension, long time, boolean advancing) implements GolemPayload {

        static final GolemCodec<SyncPayload> CODEC = GolemCodec.of(SyncPayload.class, Ids.of("golemcore", "time_of_day"),
                (payload, buffer) -> {
                    buffer.writeUtf(Ids.keyId(payload.dimension()).toString());
                    buffer.writeLong(payload.time());
                    buffer.writeBoolean(payload.advancing());
                },
                buffer -> new SyncPayload(ResourceKey.create(Registries.DIMENSION, Ids.parse(buffer.readUtf())),
                        buffer.readLong(), buffer.readBoolean()));

        @Override
        public
                //? if >=26.1 {
                net.minecraft.resources.Identifier
                //?} else {
                /*net.minecraft.resources.ResourceLocation
                *///?}
                id() {
            return CODEC.id();
        }
    }
}
