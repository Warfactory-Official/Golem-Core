package dev.golem.core.gemrender;

import dev.golem.core.render.CompatRenderState;
import dev.golem.core.render.Instancing;
import dev.golem.core.render.gem.GemBER;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** A node with no GemRender build to compile against: gem machines always draw through their {@code GemBER}. */
public final class GemRender {

    private GemRender() {}

    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {}

    public static boolean isVisualizing(LevelAccessor level) {
        return false;
    }

    public static boolean owns(BlockPos pos) {
        return false;
    }

    public static Instancing.Report report() {
        return new Instancing.Report("unsupported", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
