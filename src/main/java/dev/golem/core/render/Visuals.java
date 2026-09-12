package dev.golem.core.render;

import dev.golem.core.render.gem.GemBER;
import java.util.Objects;
import java.util.function.Supplier;
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
 * <em>same</em> instance — one declaration, two backends. Callers memoize the supplier and hand the
 * same one to both.
 *
 * <p>Instancing is an addition, never a replacement: a machine registered here must still draw
 * correctly through its vanilla renderer, because five of six nodes have no engine at all and the
 * player can switch the backend off on the sixth. {@link #owns} is how the two stay out of each
 * other's way — the vanilla path asks whether a healthy visual already claimed this frame, and returns
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
        //? if neoforge && >=26.1 {
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
     * {@link GemBER#submitUndeclared} geometry — a beam sized to where it lands, a fluid surface at its
     * fill level — has no resident mesh and no instance, so it has to keep being submitted every frame
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
        Ownership test = external;
        if (test != null && test.owns(pos)) return true;
        //? if neoforge && >=26.1 {
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
