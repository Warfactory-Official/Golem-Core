package dev.golem.core.mixin;

//? if neoforge && >=26.1 {

import dev.golem.core.flywheel.visual.VisualizationLifecycle;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the instancing engine when a client block entity arrives and leaves.
 *
 * <p>Fabric publishes {@code ClientBlockEntityEvents} for exactly this; NeoForge publishes nothing, and
 * without it the engine holds a visualizer per declared machine and is never handed a single block
 * entity to build one from — which is silent, because the machine goes on drawing through the vanilla
 * renderer that instancing was meant to relieve.
 *
 * <p>Three hooks, because a block entity can leave three ways. {@code setBlockEntity} is the one funnel
 * every arrival goes through, {@code addAndRegisterBlockEntity} included. {@code removeBlockEntity} is
 * a block being broken or replaced. {@code clearAllBlockEntities} is the chunk itself going away, which
 * is what {@code ClientLevel.unload} calls and which neither of the other two would ever report.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkVisuals {

    @Shadow
    public abstract Level getLevel();

    @Shadow
    public abstract java.util.Map<BlockPos, BlockEntity> getBlockEntities();

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void golemcore$visualAdded(BlockEntity blockEntity, CallbackInfo info) {
        Level level = getLevel();
        if (level != null && level.isClientSide()) {
            VisualizationLifecycle.onBlockEntityAdded(level, blockEntity);
        }
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void golemcore$visualRemoved(BlockPos pos, CallbackInfo info) {
        Level level = getLevel();
        if (level != null && level.isClientSide()) {
            VisualizationLifecycle.onBlockEntityRemoved(level, pos);
        }
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void golemcore$visualsCleared(CallbackInfo info) {
        Level level = getLevel();
        if (level == null || !level.isClientSide()) return;
        // Copied before iterating: the removal walks the engine's own maps, and the chunk's map is
        // about to be cleared under us by the method we are standing in front of.
        for (BlockPos pos : List.copyOf(getBlockEntities().keySet())) {
            VisualizationLifecycle.onBlockEntityRemoved(level, pos);
        }
    }
}
//?}
