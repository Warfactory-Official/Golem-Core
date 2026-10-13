package dev.golem.core.render;

import dev.golem.core.render.gem.GemBER;
import dev.golem.core.world.SubLevels;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

/**
 * Optional GPU-instanced block entities, registered without exposing an engine implementation.
 *
 * <p>The engine types live in {@code dev.golem.core.flywheel} and every one of them is gated to a
 * single node, so naming one from a consumer's feature code would put a version guard where no version
 * guard belongs. This is the same shape {@link Billboards} already uses for entities: neutral types in,
 * engine types hidden, an inert body everywhere the engine is absent.
 *
 * <p>Registration takes a supplier rather than a renderer because a {@link GemBER} builds its model
 * from loaded resources, and both this and the block-entity-renderer registry must end up holding the
 * <em>same</em> instance: one declaration, two backends. Callers memoize the supplier and hand the
 * same one to both.
 *
 * <p>Instancing is an addition, never a replacement: a machine registered here must still draw
 * correctly through its vanilla renderer, because four of six nodes have no engine of Core's own (the
 * published one on 1.20.1/1.21.1 is an optional mod), and the player can switch the backend off on the
 * two 26.1 nodes ({@code config/golemcore-flywheel.json}, {@code /flywheel backend off}), or the
 * engine switches itself off on any failure. {@link #owns} is how the two stay out of each
 * other's way: the vanilla path asks whether a healthy visual already claimed this frame, and returns
 * without drawing when it has.
 */
public final class Visuals {

    private Visuals() {}

    /** Whether Core provides an instancing engine on this node. */
    public static boolean isAvailable() {
        return Instancing.isAvailable();
    }

    /**
     * Binds a block-entity type to the instanced backend. A no-op on every node without the vendored
     * engine, so this is safe to call unguarded from registration code.
     */
    public static <T extends BlockEntity, S extends CompatRenderState> void register(
            BlockEntityType<T> type, Supplier<? extends GemBER<T, S>> renderer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(renderer, "renderer");
        //? if >=26.1 {
        dev.golem.core.flywheel.visual.GemBlockEntityVisual.register(type, renderer);
        //?}
    }

    /** A consumer's answer to {@link #owns}, for an engine Core does not ship. */
    @FunctionalInterface
    public interface Ownership {
        boolean owns(BlockPos pos);
    }

    private static @Nullable Ownership external;

    /**
     * Installs a consumer's ownership test, so a gem model bound to some other engine can still tell
     * the vanilla renderer to skip its declared parts.
     *
     * <p>Skipping the block entity renderer outright is the wrong tool for that, because a renderer's
     * {@link GemBER#submitUndeclared} geometry (a beam sized to where it lands, a fluid surface at its
     * fill level) has no resident mesh and no instance, so it has to keep being submitted every frame
     * whoever owns the declared tree.
     */
    public static void ownedBy(Ownership test) {
        external = Objects.requireNonNull(test, "test");
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
        Ownership test = external;
        if (test != null && test.owns(pos)) return true;
        //? if >=26.1 {
        var manager = dev.golem.core.flywheel.visual.VisualizationLifecycle.current();
        if (manager == null) return false;
        return manager.blockEntityVisual(pos)
                instanceof dev.golem.core.flywheel.visual.GemBlockEntityVisual<?, ?> visual
                && visual.ownsCurrentFrame();
        //?} else {
        /*return false;
        *///?}
    }
}
