package dev.golem.core.flywheel.visual;

//? if >=26.1 {

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
public final class VisualizationLifecycle {

    private static final Map<LevelAccessor, VisualizationManager> MANAGERS = new IdentityHashMap<>();

    private VisualizationLifecycle() {
    }

    public static synchronized @Nullable VisualizationManager current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        return MANAGERS.get(minecraft.level);
    }

    public static synchronized @Nullable VisualizationManager get(LevelAccessor level) {
        return MANAGERS.get(level);
    }

    public static void onLevelLoad(LevelAccessor level) {
        if (!(level instanceof ClientLevel client)) return;
        VisualizationManager manager = new VisualizationManager(client);
        VisualizationManager replaced;
        synchronized (VisualizationLifecycle.class) {
            replaced = MANAGERS.put(client, manager);
        }
        if (replaced != null) {
            replaced.shutdown();
        }
    }

    public static void onLevelUnload(LevelAccessor level) {
        if (!(level instanceof ClientLevel client)) return;
        VisualizationManager manager;
        synchronized (VisualizationLifecycle.class) {
            manager = MANAGERS.remove(client);
        }
        if (manager != null) {
            manager.shutdown();
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
            manager.shutdown();
        }
    }

    public static void onEntityJoin(LevelAccessor level, Entity entity) {
        if (!level.isClientSide()) return;
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.addEntity(entity, 0.0F);
        }
    }

    public static void onEntityLeave(LevelAccessor level, Entity entity) {
        if (!level.isClientSide()) return;
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.removeEntity(entity.getUUID());
        }
    }

    public static void onBlockEntityAdded(Level level, BlockEntity blockEntity) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.addBlockEntity(blockEntity, 0.0F);
        }
    }

    public static void onBlockEntityRemoved(Level level, BlockPos pos) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.removeBlockEntity(pos);
        }
    }

    public static void onBlockEntityResized(Level level, BlockPos pos) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.refreshBlockEntity(pos);
        }
    }

    public static void onSectionLightUpdate(Level level, long sectionKey) {
        VisualizationManager manager = get(level);
        if (manager != null) {
            manager.dispatchLightUpdate(sectionKey);
        }
    }

}
//?}
