package dev.golem.core.flywheel.visual;

//? if neoforge && >=26.1 {

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
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

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        VisualizationManager manager = new VisualizationManager(level);
        synchronized (VisualizationLifecycle.class) {
            MANAGERS.put(level, manager);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        VisualizationManager manager;
        synchronized (VisualizationLifecycle.class) {
            manager = MANAGERS.remove(level);
        }
        if (manager != null) {
            manager.shutdown();
        }
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            VisualizationManager manager = get(event.getLevel());
            if (manager != null) {
                manager.addEntity(event.getEntity(), 0.0F);
            }
        }
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            VisualizationManager manager = get(event.getLevel());
            if (manager != null) {
                manager.removeEntity(event.getEntity().getUUID());
            }
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
