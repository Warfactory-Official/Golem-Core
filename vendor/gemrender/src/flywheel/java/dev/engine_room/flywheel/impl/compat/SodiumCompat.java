package dev.engine_room.flywheel.impl.compat;

import org.jetbrains.annotations.Nullable;

import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
//? if fabric {
/*import dev.engine_room.flywheel.impl.FlwImpl;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderHandler;
import net.caffeinemc.mods.sodium.api.blockentity.BlockEntityRenderPredicate;
*///?}

// Fabric: upstream's integration, against Sodium 0.9's unchanged API. Sodium's chunk builder never calls
// SectionCompiler#handleBlockEntity, so this predicate is the only way a loaded block entity is visualized.
//? if fabric {
/*public final class SodiumCompat {
	public static final boolean ACTIVE = CompatMod.SODIUM.isLoaded;

	static {
		if (ACTIVE) {
			FlwImpl.LOGGER.debug("Detected Sodium");
		}
	}

	private SodiumCompat() {
	}

	@Nullable
	public static <T extends BlockEntity> Object onSetBlockEntityVisualizer(BlockEntityType<T> type, @Nullable BlockEntityVisualizer<? super T> oldVisualizer, @Nullable BlockEntityVisualizer<? super T> newVisualizer, @Nullable Object predicate) {
		if (!ACTIVE) {
			return null;
		}

		if (oldVisualizer == null && newVisualizer != null) {
			if (predicate != null) {
				throw new IllegalArgumentException("Sodium predicate must be null when old visualizer is null");
			}

			return Internals.addPredicate(type);
		} else if (oldVisualizer != null && newVisualizer == null) {
			if (predicate == null) {
				throw new IllegalArgumentException("Sodium predicate must not be null when old visualizer is not null");
			}

			Internals.removePredicate(type, predicate);
			return null;
		}

		return predicate;
	}

	private static final class Internals {
		static <T extends BlockEntity> Object addPredicate(BlockEntityType<T> type) {
			BlockEntityRenderPredicate<T> predicate = (getter, pos, be) -> !VisualizationHelper.tryAddBlockEntity(be);
			BlockEntityRenderHandler.instance().addRenderPredicate(type, predicate);
			return predicate;
		}

		@SuppressWarnings("unchecked")
		static <T extends BlockEntity> void removePredicate(BlockEntityType<T> type, Object predicate) {
			BlockEntityRenderHandler.instance().removeRenderPredicate(type, (BlockEntityRenderPredicate<T>) predicate);
		}
	}
}
*///?} else {
/**
 * Stubbed on 26.1: Sodium has no 26.1 build, so its API is not on the classpath and this fork drops
 * the integration rather than carrying a dependency that cannot resolve. Restore upstream's version
 * when Sodium ships -- the call sites are unchanged, only {@code Internals} went. See NOTICE.md.
 */
public final class SodiumCompat {
	public static final boolean ACTIVE = false;

	private SodiumCompat() {
	}

	@Nullable
	public static <T extends BlockEntity> Object onSetBlockEntityVisualizer(BlockEntityType<T> type, @Nullable BlockEntityVisualizer<? super T> oldVisualizer, @Nullable BlockEntityVisualizer<? super T> newVisualizer, @Nullable Object predicate) {
		return null;
	}
}
//?}
