package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A provider may choose a respawn transition before vanilla consumes or rejects the stored spawn block.
 */
public final class Respawns {
    public record Point(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public sealed interface Destination permits DefaultSpawn, WorldSpawn {
    }

    public enum DefaultSpawn implements Destination {INSTANCE}

    public record WorldSpawn(ServerLevel level) implements Destination {
    }

    public static final Destination DEFAULT = DefaultSpawn.INSTANCE;

    @FunctionalInterface
    public interface Rule {
        @Nullable Destination destination(ServerPlayer player);
    }

    /**
     * The first destination wins; null retains vanilla's spawn-block lookup.
     */
    public static final Hook<Rule> RULES = rules();
    /**
     * Selects the world spawn only after vanilla rejects the stored spawn block.
     */
    public static final Hook<Rule> FALLBACKS = rules();

    private static Hook<Rule> rules() {
        return Hook.of(listeners -> player -> {
            for (var rule : listeners) {
                var destination = rule.destination(player);
                if (destination != null) return destination;
            }
            return null;
        });
    }

    public static @Nullable Point stored(ServerPlayer player) {
        //? if >=26.1 {
        var config = player.getRespawnConfig();
        return config == null ? null : new Point(config.respawnData().dimension(), config.respawnData().pos());
        //?} else {
        /*var pos = player.getRespawnPosition();
        return pos == null ? null : new Point(player.getRespawnDimension(), pos);
        *///?}
    }

    private Respawns() {
    }
}
