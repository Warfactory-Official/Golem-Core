package dev.golem.core.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.golem.core.Ids;
import dev.golem.core.command.CompatCommands;
import dev.golem.core.command.Permissions;
import dev.golem.core.net.GolemCodec;
import dev.golem.core.net.GolemNetwork;
import dev.golem.core.net.GolemPayload;
import dev.golem.core.platform.Lifecycle;
import dev.golem.core.platform.Platform;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

/**
 * Mod-defined world rules with 1.7's {@code /gamerule} feel: a boolean, int or double under a camelCase name, saved
 * with the world and read from server code by key. Vanilla game rules are boolean/int only and 26.1 names them by
 * lowercase identifier, so each rule here is a literal under vanilla's own {@code gamerule} command node instead.
 * Values are text in the overworld's saved data (default when unset, clamped when read, as 1.7); clients get the whole
 * map on join and on every change. Declare rules at mod construction on both sides.
 */
public final class WorldRules {

    private WorldRules() {}

    private static final String DATA_ID = "golemcore:world_rules";
    private static final Map<String, Rule<?>> RULES = new ConcurrentHashMap<>();
    private static final Map<String, String> CLIENT = new ConcurrentHashMap<>();
    private static volatile @Nullable Store store;
    private static boolean hooked;

    /** A boolean rule. */
    public static BoolRule bool(String name, boolean defaultValue) {
        return add(new BoolRule(name, defaultValue));
    }

    /** An int rule; stored as typed, clamped to {@code [min, max]} when read. */
    public static IntRule integer(String name, int defaultValue, int min, int max) {
        return add(new IntRule(name, defaultValue, min, max));
    }

    /** A double rule; stored as typed, clamped to {@code [min, max]} when read. */
    public static DoubleRule decimal(String name, double defaultValue, double min, double max) {
        return add(new DoubleRule(name, defaultValue, min, max));
    }

    /** Puts every listed rule back to its default in the server's world, and tells the players. */
    public static void resetAll(MinecraftServer server, Rule<?>... rules) {
        Map<String, String> values = data(server).values;
        for (Rule<?> rule : rules) values.remove(rule.name);
        changed(server);
    }

    private static synchronized <R extends Rule<?>> R add(R rule) {
        if (RULES.putIfAbsent(rule.name, rule) != null) throw new IllegalStateException("World rule declared twice: " + rule.name);
        hook();
        return rule;
    }

    private static void hook() {
        if (hooked) return;
        hooked = true;
        GolemNetwork.declarePlayToClient(SyncPayload.CODEC);
        if (Platform.isClient()) {
            GolemNetwork.registerPlayToClient(SyncPayload.CODEC, (payload, context) -> {
                CLIENT.clear();
                CLIENT.putAll(payload.values());
            });
        }
        CompatCommands.server(WorldRules::registerCommands);
        Lifecycle.onServerStarted(server -> data(server));
        Lifecycle.onServerStopping(server -> store = null);
        Lifecycle.onPlayerJoin((player, firstTime) -> send(player, data(player.level().getServer()).values));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("gamerule").requires(Permissions.gamemasters());
        for (Rule<?> rule : RULES.values()) root.then(rule.command());
        dispatcher.register(root);
    }

    /** A client level reads what the server sent, even in singleplayer, so what the client shows is what arrived. */
    private static Map<String, String> values(@Nullable LevelReader level) {
        if (level instanceof ServerLevel server) return data(server.getServer()).values;
        if (level != null && level.isClientSide()) return CLIENT;
        Store current = store;
        return current != null ? current.data.values : CLIENT;
    }

    private static Data data(MinecraftServer server) {
        Store current = store;
        if (current == null || current.server != server) {
            current = new Store(server, CompatSavedData.get(server.overworld(), DATA_ID, Data::new, Data.CODEC));
            store = current;
        }
        return current.data;
    }

    private static void changed(MinecraftServer server) {
        Data data = data(server);
        data.setDirty();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, data.values);
    }

    private static void send(ServerPlayer player, Map<String, String> values) {
        if (!GolemNetwork.canSendToPlayer(player, SyncPayload.CODEC)) return;
        GolemNetwork.sendToPlayer(player, SyncPayload.CODEC, new SyncPayload(new HashMap<>(values)));
    }

    /** One rule. Read with the typed {@code get}, set from the server with {@link #set}. */
    public abstract static class Rule<T> {
        final String name;
        final T defaultValue;
        private volatile @Nullable Cached<T> cached;

        Rule(String name, T defaultValue) {
            this.name = name;
            this.defaultValue = defaultValue;
        }

        /** The parsed and clamped value of {@code raw}, or null when it is not a value of this type. */
        abstract @Nullable T parse(String raw);

        abstract ArgumentType<?> argument();

        abstract String fromCommand(CommandContext<CommandSourceStack> context);

        public String name() {
            return name;
        }

        public T defaultValue() {
            return defaultValue;
        }

        /** The value in {@code level}'s world; any reader of the same world gives the same answer. */
        public T value(@Nullable LevelReader level) {
            String raw = values(level).get(name);
            if (raw == null) return defaultValue;
            Cached<T> hit = cached;
            if (hit != null && hit.raw.equals(raw)) return hit.value;
            T parsed = parse(raw);
            T result = parsed != null ? parsed : defaultValue;
            cached = new Cached<>(raw, result);
            return result;
        }

        /** Sets the rule in the server's world. */
        public void set(MinecraftServer server, T value) {
            setRaw(server, String.valueOf(value));
        }

        /** Puts the rule back to its default in the server's world. */
        public void reset(MinecraftServer server) {
            data(server).values.remove(name);
            changed(server);
        }

        private void setRaw(MinecraftServer server, String raw) {
            data(server).values.put(name, raw);
            changed(server);
        }

        private String shown(CommandSourceStack source) {
            String raw = values(source.getLevel()).get(name);
            return raw != null ? raw : String.valueOf(defaultValue);
        }

        private LiteralArgumentBuilder<CommandSourceStack> command() {
            return Commands.literal(name)
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        source.sendSuccess(() -> Component.translatable("commands.gamerule.query", name, shown(source)), false);
                        return 1;
                    })
                    .then(Commands.argument("value", argument()).executes(context -> {
                        CommandSourceStack source = context.getSource();
                        String raw = fromCommand(context);
                        setRaw(source.getServer(), raw);
                        source.sendSuccess(() -> Component.translatable("commands.gamerule.set", name, raw), true);
                        return 1;
                    }));
        }
    }

    private record Cached<T>(String raw, T value) {}

    public static final class BoolRule extends Rule<Boolean> {
        BoolRule(String name, boolean defaultValue) {
            super(name, defaultValue);
        }

        public boolean get(@Nullable LevelReader level) {
            return value(level);
        }

        @Override Boolean parse(String raw) {
            return raw.equals("true") ? Boolean.TRUE : raw.equals("false") ? Boolean.FALSE : null;
        }

        @Override ArgumentType<?> argument() {
            return BoolArgumentType.bool();
        }

        @Override String fromCommand(CommandContext<CommandSourceStack> context) {
            return String.valueOf(BoolArgumentType.getBool(context, "value"));
        }
    }

    public static final class IntRule extends Rule<Integer> {
        private final int min;
        private final int max;

        IntRule(String name, int defaultValue, int min, int max) {
            super(name, defaultValue);
            this.min = min;
            this.max = max;
        }

        public int get(@Nullable LevelReader level) {
            return value(level);
        }

        @Override Integer parse(String raw) {
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
            } catch (NumberFormatException expected) {
                return null; // a hand-edited value that is not a number reads as the default
            }
        }

        @Override ArgumentType<?> argument() {
            return IntegerArgumentType.integer();
        }

        @Override String fromCommand(CommandContext<CommandSourceStack> context) {
            return String.valueOf(IntegerArgumentType.getInteger(context, "value"));
        }
    }

    public static final class DoubleRule extends Rule<Double> {
        private final double min;
        private final double max;

        DoubleRule(String name, double defaultValue, double min, double max) {
            super(name, defaultValue);
            this.min = min;
            this.max = max;
        }

        public double get(@Nullable LevelReader level) {
            return value(level);
        }

        @Override Double parse(String raw) {
            try {
                double parsed = Double.parseDouble(raw.trim());
                return Double.isNaN(parsed) ? null : Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException expected) {
                return null; // a hand-edited value that is not a number reads as the default
            }
        }

        @Override ArgumentType<?> argument() {
            return DoubleArgumentType.doubleArg();
        }

        @Override String fromCommand(CommandContext<CommandSourceStack> context) {
            return String.valueOf(DoubleArgumentType.getDouble(context, "value"));
        }
    }

    private record Store(MinecraftServer server, Data data) {}

    /** The overworld's saved values, name to text. */
    static final class Data extends CompatSavedData<Data> {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("rules", Map.of()).forGetter(data -> Map.copyOf(data.values))
        ).apply(instance, Data::new));

        final Map<String, String> values = new ConcurrentHashMap<>();

        Data() {}

        Data(Map<String, String> saved) {
            values.putAll(saved);
        }

        @Override
        protected Codec<Data> codec() {
            return CODEC;
        }
    }

    /** S2C: every saved rule value of the world the player stands in. */
    record SyncPayload(Map<String, String> values) implements GolemPayload {

        static final GolemCodec<SyncPayload> CODEC = GolemCodec.of(SyncPayload.class, Ids.of("golemcore", "world_rules"),
                (payload, buffer) -> {
                    buffer.writeVarInt(payload.values().size());
                    payload.values().forEach((key, value) -> {
                        buffer.writeUtf(key);
                        buffer.writeUtf(value);
                    });
                },
                buffer -> {
                    int size = buffer.readVarInt();
                    Map<String, String> values = new HashMap<>();
                    for (int i = 0; i < size; i++) values.put(buffer.readUtf(), buffer.readUtf());
                    return new SyncPayload(values);
                });

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
