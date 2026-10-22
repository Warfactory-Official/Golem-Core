package dev.golem.core.render;

import dev.golem.core.gemrender.GemRender;
import dev.golem.core.render.gem.GemBER;
import dev.golem.core.world.SubLevels;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Binds gem-declared block entities to GemRender. The supplier must be the one the block-entity-renderer registry holds
 * (one {@link GemBER}, two backends); {@link #owns} keeps the two from drawing the same parts.
 */
public final class Visuals {

    private Visuals() {}

    /** Binds a block-entity type to GemRender. A no-op without it, so safe to call unguarded. */
    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(renderer, "renderer");
        if (Instancing.isAvailable()) GemRender.register(type, renderer);
    }

    /**
     * Whether a healthy instanced visual already drew the block entity at {@code pos} this frame, so
     * the vanilla renderer should leave it alone. False whenever no engine owns it, which is the
     * answer whenever the backend is off or absent.
     */
    public static boolean owns(BlockPos pos) {
        // A block on a sub-level whose system does not carry instanced visuals (Sable on Fabric has no Flywheel
        // embedding) is drawn by the engine where its block sits, far from where it is seen: never owned, so its
        // renderer draws the whole model in place.
        if (SubLevels.strandsInstancedVisuals(Minecraft.getInstance().level, pos)) {
            return false;
        }
        return Instancing.isAvailable() && GemRender.owns(pos);
    }
}
