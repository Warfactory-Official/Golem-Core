package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import dev.golem.core.GolemCore;
import dev.golem.core.flywheel.EngineState;
import dev.golem.core.flywheel.backend.BackendManager;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * One visualization manager per client level, created and destroyed from the loader's events.
 *
 * <p>Every entry point here is guarded by {@link EngineState}: while the engine is off — switched off
 * by the player, or stopped by a failure — {@link #current} and {@link #get} answer {@code null}, which
 * is what makes {@code Visuals.owns} and {@code Billboards.owns} answer no and every vanilla renderer
 * draw its machine. The managers themselves are torn down at the next safe point, never from inside a
 * frame walk that is still using them.
 */
public final class VisualizationLifecycle {

    private static final Map<LevelAccessor, VisualizationManager> MANAGERS = new IdentityHashMap<>();

    private VisualizationLifecycle() {
    }

    public static synchronized @Nullable VisualizationManager current() {
        if (!EngineState.running()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        return MANAGERS.get(minecraft.level);
    }

    public static synchronized @Nullable VisualizationManager get(LevelAccessor level) {
        if (!EngineState.running()) return null;
        return MANAGERS.get(level);
    }

    public static void onLevelLoad(LevelAccessor level) {
        if (!(level instanceof ClientLevel client) || !EngineState.running()) return;
        EngineState.guard("level load", () -> {
            // Asked before constructing anything, so "no usable backend" is a quiet off rather than a
            // manager half-built around nothing.
            if (BackendManager.current() == null) {
                EngineState.switchOff("no supported backend");
                return;
            }
            VisualizationManager manager = new VisualizationManager(client);
            VisualizationManager replaced;
            synchronized (VisualizationLifecycle.class) {
                replaced = MANAGERS.put(client, manager);
            }
            if (replaced != null) {
                replaced.shutdown();
            }
        });
    }

    public static void onLevelUnload(LevelAccessor level) {
        if (!(level instanceof ClientLevel client)) return;
        VisualizationManager manager;
        synchronized (VisualizationLifecycle.class) {
            manager = MANAGERS.remove(client);
        }
        if (manager != null) {
            shutdownQuietly(manager);
        }
    }

    // Fabric announces the level the client moved to and never the one it left, so the departure
    // has to be inferred: everything that is not the level now being rendered is gone.
    public static void retainOnly(@Nullable LevelAccessor keep) {
        List<VisualizationManager> closing = new ArrayList<>();
        synchronized (VisualizationLifecycle.class) {
            MANAGERS.entrySet().removeIf(entry -> {
                if (entry.getKey() == keep) return false;
                closing.add(entry.getValue());
                return true;
            });
        }
        for (VisualizationManager manager : closing) {
            shutdownQuietly(manager);
        }
    }

    /**
     * Tears every manager down if the engine was switched off since the last safe point. Called at the
     * start of each frame and tick, and directly by the {@code /flywheel} command.
     */
    public static void teardownIfPending() {
        if (!EngineState.takeTeardown()) return;
        GolemRenderSystem.settle();
        retainOnly(null);
        try {
            dev.golem.core.flywheel.MeshPool.releaseGpuResources();
        } catch (RuntimeException | LinkageError ignored) {
            // The pool is rebuilt on demand; failing to free it early costs memory, not correctness.
        }
        GolemCore.LOG.info("Golem flywheel stopped ({}); machines draw through their vanilla renderers",
                EngineState.offReason());
    }

    /**
     * Builds the current level's manager again and hands it everything already loaded — the path
     * {@code /flywheel backend DEFAULT} takes after {@code off}, since the chunk events that normally
     * feed a manager fired long ago.
     */
    public static void rebuildCurrent() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        onLevelLoad(level);
        VisualizationManager manager = get(level);
        if (manager == null) return;
        EngineState.guard("rebuild", () -> {
            var eye = minecraft.gameRenderer.getMainCamera().position();
            ChunkPos centre = new ChunkPos((int) Math.floor(eye.x) >> 4, (int) Math.floor(eye.z) >> 4);
            int radius = minecraft.options.getEffectiveRenderDistance() + 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(centre.x() + dx, centre.z() + dz);
                    if (chunk == null) continue;
                    for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
                        manager.addBlockEntity(blockEntity, 0.0F);
                    }
                }
            }
            for (Entity entity : level.entitiesForRendering()) {
                manager.addEntity(entity, 0.0F);
            }
        });
    }

    public static void onEntityJoin(LevelAccessor level, Entity entity) {
        if (!level.isClientSide()) return;
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("entity join", () -> manager.addEntity(entity, 0.0F));
        }
    }

    public static void onEntityLeave(LevelAccessor level, Entity entity) {
        if (!level.isClientSide()) return;
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("entity leave", () -> manager.removeEntity(entity.getUUID()));
        }
    }

    public static void onBlockEntityAdded(Level level, BlockEntity blockEntity) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("block entity load", () -> manager.addBlockEntity(blockEntity, 0.0F));
        }
    }

    public static void onBlockEntityRemoved(Level level, BlockPos pos) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("block entity unload", () -> manager.removeBlockEntity(pos));
        }
    }

    public static void onBlockEntityResized(Level level, BlockPos pos) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("block entity resize", () -> manager.refreshBlockEntity(pos));
        }
    }

    public static void onSectionLightUpdate(Level level, long sectionKey) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            EngineState.guard("light update", () -> manager.dispatchLightUpdate(sectionKey));
        }
    }

    private static void shutdownQuietly(VisualizationManager manager) {
        try {
            manager.shutdown();
        } catch (RuntimeException | LinkageError failure) {
            // Shutting down is the fallback itself; a failure here must not escape into the loader.
            GolemCore.LOG.debug("Golem flywheel manager shutdown failed", failure);
        }
    }

}
//?}
