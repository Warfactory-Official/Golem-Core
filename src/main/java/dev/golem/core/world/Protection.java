package dev.golem.core.world;

import dev.golem.core.hook.Hook;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Mod-agnostic land protection for non-player causes: explosions, spreading effects and hazards that change blocks or
 * hurt entities without a player-break or player-place event. Protection mods register a {@link Provider}; code that
 * alters the world asks a {@link Gate} (one per operation) or the single-shot methods. Server side only.
 *
 * <p>Deny wins: a position is altered only if no provider objects. With no provider every query is one volatile read
 * and a constant.
 */
public final class Protection {

    private Protection() {}

    /** Why blocks change; providers map each to their own rule. */
    public enum Cause {
        EXPLOSION, FIRE, FLUID, RADIATION_BLOCK_CONVERSION, TERRAFORM, PLACEMENT
    }

    /** What reaches an entity; providers map each to their own rule. */
    public enum Hazard {
        RADIATION, CONTAMINATION, GAS, FIRE, BLAST
    }

    /** How much of a chunk a provider's answer covers. */
    public enum Coverage {
        /** Provider objects to nothing here. */
        OPEN,
        /** Provider objects to everything here. */
        CLOSED,
        /** Provider decides per position via {@link Provider#mayAlter}. */
        PARTIAL
    }

    /** Implemented by entities that carry a persisted responsible player; consulted first by {@link Source#of}. */
    public interface Owned {
        @Nullable UUID protectionOwner();
    }

    /**
     * Who is responsible. Either id may be null; both null = environment or unknown. Ids are opaque to Golem Core: a
     * provider resolves a player to a faction (or the reverse) itself.
     */
    public record Source(@Nullable UUID player, @Nullable UUID faction) {

        public static final Source NONE = new Source(null, null);

        public static Source ofPlayer(@Nullable UUID player) {
            return player == null ? NONE : new Source(player, null);
        }

        /** The responsible player behind {@code entity}: itself, an {@link Owned} entity's owner, a primed TNT's igniter, or a projectile's owner. */
        public static Source of(@Nullable Entity entity) {
            if (entity instanceof Owned owned) return ofPlayer(owned.protectionOwner());
            if (entity instanceof Player player) return ofPlayer(player.getUUID());
            if (entity instanceof PrimedTnt tnt && tnt.getOwner() != null) return ofPlayer(tnt.getOwner().getUUID());
            if (entity instanceof Projectile projectile && projectile.getOwner() != null) {
                return of(projectile.getOwner());
            }
            return NONE;
        }
    }

    public interface Provider {
        /** Chunk-level answer; {@link Coverage#OPEN} and {@link Coverage#CLOSED} skip per-position calls. Must not load chunks. */
        default Coverage coverage(Level level, int chunkX, int chunkZ, Source source, Cause cause) {
            return Coverage.PARTIAL;
        }

        /** {@code pos} may be mutable: copy to retain. */
        default boolean mayAlter(Level level, BlockPos pos, Source source, Cause cause) {
            return true;
        }

        default boolean mayHarm(Level level, Entity victim, Source source, Hazard hazard) {
            return true;
        }
    }

    public static final Hook<Provider> PROVIDERS = Hook.notifying();

    public static void register(Provider provider) {
        PROVIDERS.register(provider);
    }

    public static void unregister(Provider provider) {
        PROVIDERS.remove(provider);
    }

    /** Whether any provider is registered; lets hot loops skip all work. */
    public static boolean active() {
        return !PROVIDERS.isEmpty();
    }

    /** Same answer as a {@link Gate}: a provider's chunk coverage first, its per-position answer only if partial. */
    public static boolean mayAlter(Level level, BlockPos pos, Source source, Cause cause) {
        var providers = PROVIDERS.listeners();
        for (int i = 0; i < providers.size(); i++) {
            Provider provider = providers.get(i);
            Coverage coverage = provider.coverage(level, pos.getX() >> 4, pos.getZ() >> 4, source, cause);
            if (coverage == Coverage.CLOSED) return false;
            if (coverage == Coverage.PARTIAL && !provider.mayAlter(level, pos, source, cause)) return false;
        }
        return true;
    }

    public static boolean mayHarm(Level level, Entity victim, Source source, Hazard hazard) {
        var providers = PROVIDERS.listeners();
        for (int i = 0; i < providers.size(); i++) {
            if (!providers.get(i).mayHarm(level, victim, source, hazard)) return false;
        }
        return true;
    }

    /** Per-operation gate, safe to keep for a whole long-running effect: chunk decisions are cached for one game tick. */
    public static Gate gate(Level level, Source source, Cause cause) {
        if (level.isClientSide() || PROVIDERS.isEmpty()) return Gate.OPEN;
        return new Gate(level, source, cause, PROVIDERS.listeners(), level::getGameTime);
    }

    /** Not thread safe; one gate per thread. */
    public static final class Gate {
        private static final byte S_OPEN = 0;
        private static final byte S_CLOSED = 1;
        private static final byte S_PARTIAL = 2;

        /** Allows everything: no provider, or a client level. */
        public static final Gate OPEN = new Gate(null, Source.NONE, Cause.EXPLOSION, null, null);

        private final Level level;
        private final Source source;
        private final Cause cause;
        private final List<Provider> providers;
        private final Long2ByteOpenHashMap chunks;
        private final LongSupplier clock;
        private long cacheTick = Long.MIN_VALUE;
        private long lastKey = Long.MIN_VALUE;
        private byte last;

        Gate(Level level, Source source, Cause cause, List<Provider> providers, LongSupplier clock) {
            this.clock = clock;
            this.level = level;
            this.source = source;
            this.cause = cause;
            this.providers = providers;
            this.chunks = providers == null ? null : new Long2ByteOpenHashMap();
            if (chunks != null) chunks.defaultReturnValue((byte) -1);
        }

        public boolean isOpen() {
            return providers == null;
        }

        public boolean mayAlter(BlockPos pos) {
            return providers == null || mayAlter(pos.getX(), pos.getY(), pos.getZ(), pos);
        }

        /** Allocates a {@link BlockPos} only when a provider is {@link Coverage#PARTIAL} for the chunk. */
        public boolean mayAlter(int x, int y, int z) {
            return providers == null || mayAlter(x, y, z, null);
        }

        private boolean mayAlter(int x, int y, int z, @Nullable BlockPos pos) {
            byte state = state(x >> 4, z >> 4);
            if (state == S_OPEN) return true;
            if (state == S_CLOSED) return false;
            BlockPos at = pos != null ? pos : new BlockPos(x, y, z);
            for (int i = 0; i < providers.size(); i++) {
                if (!providers.get(i).mayAlter(level, at, source, cause)) return false;
            }
            return true;
        }

        /** Combined chunk answer: CLOSED if any provider closes it, PARTIAL if any is partial, else OPEN. */
        public Coverage coverage(int chunkX, int chunkZ) {
            if (providers == null) return Coverage.OPEN;
            return switch (state(chunkX, chunkZ)) {
                case S_OPEN -> Coverage.OPEN;
                case S_CLOSED -> Coverage.CLOSED;
                default -> Coverage.PARTIAL;
            };
        }

        private byte state(int chunkX, int chunkZ) {
            long tick = clock.getAsLong();
            if (tick != cacheTick) {
                cacheTick = tick;
                chunks.clear();
                lastKey = Long.MIN_VALUE;
            }
            long key = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
            if (key == lastKey) return last;
            byte state = chunks.get(key);
            if (state < 0) {
                state = resolve(chunkX, chunkZ);
                chunks.put(key, state);
            }
            lastKey = key;
            last = state;
            return state;
        }

        private byte resolve(int chunkX, int chunkZ) {
            byte result = S_OPEN;
            for (int i = 0; i < providers.size(); i++) {
                switch (providers.get(i).coverage(level, chunkX, chunkZ, source, cause)) {
                    case CLOSED:
                        return S_CLOSED;
                    case PARTIAL:
                        result = S_PARTIAL;
                        break;
                    default:
                        break;
                }
            }
            return result;
        }

        /** Removes denied positions in place; returns whether any was removed. */
        public boolean removeDenied(Collection<BlockPos> positions) {
            return providers != null && positions.removeIf(pos -> !mayAlter(pos));
        }
    }
}
